package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.model.AuditChangeMode;
import com.erp.manufacturing.common.audit.model.AuditOperation;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchResponse;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportRowRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.dataimport.target.BomImportHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The transactional half of a fixed-template commit (G3). A bean of its own so that
 * {@link DataImportBatchService} can call into it through the Spring proxy: each public method here is
 * a separate transaction, which is the whole point — the commit must roll back as one unit, and the
 * "mark INVALID" that follows a stale commit must survive that rollback.
 *
 * <p>Atomicity (FE R03/A15/A18): the masters are created through their own services, which join this
 * transaction. Any failure on any row — including the unique index of a concurrent batch that took the
 * same code — rolls back every master of the batch and leaves it {@code READY}. There is no per-row
 * transaction here, unlike {@link ImportRowApplyService} of the mapped-profile path.
 */
@Component
@RequiredArgsConstructor
public class DataImportBatchCommitter {

    private final ImportRunRepository runRepository;
    private final ImportRowRepository rowRepository;
    private final ImportTargetRegistry registry;
    private final BomImportHandler bomImportHandler;
    private final FixedTemplateValidator validator;
    private final IdempotencySupport idempotency;
    private final DataImportMapper mapper;
    private final EntityManager entityManager;

    /**
     * Thrown when a master write fails mid-commit. Carries the real cause so the caller can decide,
     * after the rollback, whether the batch went stale or the database simply failed.
     */
    public static class MasterWriteFailedException extends RuntimeException {
        public MasterWriteFailedException(RuntimeException cause) {
            super(cause.getMessage(), cause);
        }

        public RuntimeException failure() {
            return (RuntimeException) getCause();
        }
    }

    /**
     * Receipt of an already committed batch, without taking the lock or writing an audit event — a
     * retry after a lost response must not look like a second commit (FE R04). The key is checked
     * before the receipt is handed out (FE §3.6): the same key on another batch, or with another
     * {@code expectedVersion}, is a conflict, never a receipt.
     */
    @Transactional(readOnly = true)
    public Optional<DataImportBatchResponse> findCommittedReceipt(UUID batchId, UUID ownerUserId, String key,
                                                                  long expectedVersion) {
        ImportRun batch = runRepository.findByImportRunIdAndKind(batchId, ImportRunKind.FIXED_TEMPLATE)
                .filter(run -> ownerUserId.equals(run.getOwnerUserId()))
                .orElseThrow(() -> notFound(batchId));
        checkCommitKey(batch, key, expectedVersion);
        return batch.getStatus() == ImportRunStatus.COMMITTED
                ? Optional.of(response(batch))
                : Optional.empty();
    }

    @Transactional
    @Auditable(action = AuditAction.IMPORT_BATCH_COMMITTED, entityType = "ImportRun",
            entityId = "#batchId", companyId = "#result?.companyId()", plantId = "#result?.plantId()",
            changeMode = AuditChangeMode.NONE, operation = AuditOperation.UPDATE)
    public DataImportBatchResponse commit(UUID batchId, UUID ownerUserId, long expectedVersion, String key) {
        ImportRun batch = lockOwned(batchId, ownerUserId);
        checkCommitKey(batch, key, expectedVersion);
        if (batch.getStatus() == ImportRunStatus.COMMITTED) {
            return response(batch);
        }
        if (batch.getStatus() != ImportRunStatus.READY) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Import batch in status " + batch.getStatus() + " cannot be committed; "
                            + "fix the file and upload it again");
        }
        if (!Objects.equals(batch.getVersion(), expectedVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Import batch changed since it was read; reload it and confirm again");
        }

        DataImportKind kind = kindOf(batch);
        UUID companyId = batch.getCompany().getCompanyId();
        List<ImportRow> rows = rowRepository.findByImportRunImportRunIdOrderByRowNumberAsc(batchId);
        List<FixedTemplateValidator.RowResult> results = validator.validate(kind, companyId, batch.getLocale(),
                inputs(rows));
        if (results.stream().anyMatch(result -> !result.valid())) {
            throw ExceptionFactory.custom(BusinessErrorCode.IMPORT_REVALIDATION_FAILED,
                    "Import batch is no longer valid; nothing was created");
        }

        try {
            if (kind == DataImportKind.BOMS) {
                // One BOM per group; every row of the group records the BOM it became (G5).
                List<UUID> created = bomImportHandler.apply(companyId,
                        rows.stream().map(ImportRow::getMappedValues).toList());
                for (int index = 0; index < rows.size(); index++) {
                    rows.get(index).markApplied(created.get(index));
                }
            } else {
                ImportTargetHandler handler = registry.handlerFor(kind.targetType());
                for (ImportRow row : rows) {
                    row.markApplied(handler.apply(companyId,
                            FixedTemplateSchema.toHandlerValues(kind, row.getMappedValues())));
                }
            }
            // Surfaces a unique-index violation here, inside the try, instead of at commit time where
            // it could no longer be told apart from any other failure of the transaction.
            entityManager.flush();
        } catch (RuntimeException failure) {
            throw new MasterWriteFailedException(failure);
        }

        batch.markCommitted(Instant.now());
        if (key != null) {
            batch.setIdempotencyKey(key);
            batch.setPayloadHash(idempotency.payloadHash(new CommitFingerprint(batchId, expectedVersion)));
        }
        rowRepository.saveAll(rows);
        // saveAndFlush, not save: the response carries the bumped version (lesson of CLAUDE.md §0.39).
        return mapper.toBatchResponse(runRepository.saveAndFlush(batch), rows);
    }

    /**
     * After a failed commit rolled back: validates the batch again against the database as it is now.
     * Stale ⇒ the new issues are stored and the batch becomes {@code INVALID}; still valid ⇒ nothing is
     * written and the batch stays {@code READY} (the failure was not about the data — FE §1.4).
     */
    @Transactional
    public Optional<DataImportBatchResponse> markInvalidIfStale(UUID batchId, UUID ownerUserId) {
        ImportRun batch = lockOwned(batchId, ownerUserId);
        if (batch.getStatus() != ImportRunStatus.READY) {
            return Optional.empty();
        }
        List<ImportRow> rows = rowRepository.findByImportRunImportRunIdOrderByRowNumberAsc(batchId);
        List<FixedTemplateValidator.RowResult> results = validator.validate(kindOf(batch),
                batch.getCompany().getCompanyId(), batch.getLocale(), inputs(rows));
        long errors = results.stream().filter(result -> !result.valid()).count();
        if (errors == 0) {
            return Optional.empty();
        }
        for (int index = 0; index < rows.size(); index++) {
            FixedTemplateValidator.RowResult result = results.get(index);
            if (result.valid()) {
                rows.get(index).markValid(result.values());
            } else {
                rows.get(index).markInvalid(result.values(), List.copyOf(result.issues()));
            }
        }
        batch.markBatchValidated(Instant.now(), rows.size(), (int) errors);
        rowRepository.saveAll(rows);
        return Optional.of(mapper.toBatchResponse(runRepository.saveAndFlush(batch), rows));
    }

    private ImportRun lockOwned(UUID batchId, UUID ownerUserId) {
        return runRepository.lockFixedTemplateBatch(batchId)
                .filter(run -> ownerUserId.equals(run.getOwnerUserId()))
                .orElseThrow(() -> notFound(batchId));
    }

    private void checkCommitKey(ImportRun batch, String key, long expectedVersion) {
        if (key == null) {
            return;
        }
        Optional<ImportRun> holder = runRepository.findByIdempotencyKey(key);
        if (holder.isEmpty()) {
            return;
        }
        if (!holder.get().getImportRunId().equals(batch.getImportRunId())) {
            throw ExceptionFactory.custom(BusinessErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key was already used for another import batch");
        }
        idempotency.ensureSamePayload(holder.get().getPayloadHash(),
                new CommitFingerprint(batch.getImportRunId(), expectedVersion));
    }

    private List<FixedTemplateValidator.RowInput> inputs(List<ImportRow> rows) {
        return rows.stream()
                .map(row -> new FixedTemplateValidator.RowInput(row.getRowNumber(), row.getMappedValues(), List.of()))
                .toList();
    }

    private DataImportBatchResponse response(ImportRun batch) {
        return mapper.toBatchResponse(batch,
                rowRepository.findByImportRunImportRunIdOrderByRowNumberAsc(batch.getImportRunId()));
    }

    private DataImportKind kindOf(ImportRun batch) {
        return DataImportKind.fromTargetType(batch.getTargetType())
                .orElseThrow(() -> new IllegalStateException("No import kind for " + batch.getTargetType()));
    }

    private RuntimeException notFound(UUID batchId) {
        return ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Import batch", batchId);
    }

    private record CommitFingerprint(UUID batchId, long expectedVersion) {
    }
}
