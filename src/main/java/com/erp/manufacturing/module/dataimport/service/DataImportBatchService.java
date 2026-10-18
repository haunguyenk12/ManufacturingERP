package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.model.AuditChangeMode;
import com.erp.manufacturing.common.audit.model.AuditOperation;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ResultCarryingException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.config.DataImportProperties;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRowStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchResponse;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchSummaryResponse;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportRowRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.dataimport.security.DataImportBatchPermissionGuard;
import com.erp.manufacturing.module.dataimport.spreadsheet.FixedTemplateReader;
import com.erp.manufacturing.module.dataimport.spreadsheet.FixedTemplateWorkbook;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The fixed-template, all-or-nothing import path (G3, {@code /v1/data-import/batches}).
 *
 * <p>Upload reads and validates the whole file in one call and stores a batch {@code READY} or
 * {@code INVALID} with every row and issue — and creates no master data at all (FE R02). Commit
 * creates every row of a {@code READY} batch in one transaction ({@link DataImportBatchCommitter}) or
 * none of them. The mapped-profile path ({@link ImportRunService}) is untouched and keeps its per-row
 * semantics; the two share tables but never see each other's rows.
 */
@Service
@RequiredArgsConstructor
public class DataImportBatchService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportRunRepository runRepository;
    private final ImportRowRepository rowRepository;
    private final FixedTemplateReader reader;
    private final FixedTemplateValidator validator;
    private final DataImportBatchCommitter committer;
    private final DataImportBatchPermissionGuard batchGuard;
    private final OrganizationLookupService organizationLookupService;
    private final IdempotencySupport idempotency;
    private final DataImportProperties properties;
    private final DataImportMapper mapper;
    private final AuditorAware<UUID> auditorAware;
    private final EntityManager entityManager;

    /**
     * Upload + validate. {@code plantId} is required for items and ignored for partners (the
     * controller enforces both before this runs, so a missing plant is a 400, not a 403).
     *
     * <p>Replaying the same {@code Idempotency-Key} with the same file, kind and scope returns the
     * batch created the first time; anything else under that key is a 409 (FE A25).
     */
    @Transactional
    @PreAuthorize("@dataImportBatchGuard.canImport(authentication, #kind, #companyId, #plantId)")
    @Auditable(action = AuditAction.IMPORT_BATCH_VALIDATED, entityType = "ImportRun",
            entityId = "#result?.id()", companyId = "#result?.companyId()", plantId = "#result?.plantId()",
            changeMode = AuditChangeMode.NONE, operation = AuditOperation.CREATE)
    public DataImportBatchResponse upload(MultipartFile file, DataImportKind kind, UUID companyId,
                                          UUID plantId, String idempotencyKey) {
        UUID owner = currentUser();
        String fileName = checkFile(file);
        byte[] bytes = readBytes(file);
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = kind.plantScoped() ? activePlantOf(company, plantId) : null;

        String key = StringUtils.hasText(idempotencyKey) ? idempotency.normalizeKey(idempotencyKey) : null;
        String sha256 = sha256(bytes);
        UploadFingerprint fingerprint = new UploadFingerprint(sha256, fileName, kind.wireValue(), companyId,
                plant == null ? null : plant.getPlantId());
        if (key != null) {
            Optional<ImportRun> replay = runRepository.findByOwnerUserIdAndUploadIdempotencyKey(owner, key);
            if (replay.isPresent()) {
                idempotency.ensureSamePayload(replay.get().getUploadPayloadHash(), fingerprint);
                return mapper.toBatchResponse(replay.get(),
                        rowRepository.findByImportRunImportRunIdOrderByRowNumberAsc(replay.get().getImportRunId()));
            }
        }

        FixedTemplateWorkbook workbook = reader.read(bytes, kind, properties.fixedTemplateMaxRows());
        List<FixedTemplateValidator.RowResult> results = validator.validate(kind, companyId, workbook.locale(),
                workbook.rows().stream()
                        .map(row -> new FixedTemplateValidator.RowInput(row.line(), row.values(), row.issues()))
                        .toList());
        int errors = (int) results.stream().filter(result -> !result.valid()).count();

        ImportRun batch = ImportRun.builder()
                .kind(ImportRunKind.FIXED_TEMPLATE)
                .ownerUserId(owner)
                .targetType(kind.targetType())
                .company(company)
                .plant(plant)
                .originalFilename(fileName)
                .fileSizeBytes((long) bytes.length)
                .fileSha256(sha256)
                .detectedHeaders(workbook.headers())
                .locale(workbook.locale())
                .templateVersion(workbook.templateVersion())
                .parsedAt(Instant.now())
                .uploadIdempotencyKey(key)
                .uploadPayloadHash(key == null ? null : idempotency.payloadHash(fingerprint))
                .build();
        batch.markBatchValidated(Instant.now(), results.size(), errors);
        batch = runRepository.saveAndFlush(batch);
        // Answer with what was stored: createdAt is stamped in memory at 100 ns and Postgres rounds it
        // to microseconds, so without this the upload response and every later read disagree.
        entityManager.refresh(batch);

        List<ImportRow> rows = new ArrayList<>(results.size());
        for (int index = 0; index < results.size(); index++) {
            FixedTemplateValidator.RowResult result = results.get(index);
            ImportRow row = ImportRow.builder()
                    .importRun(batch)
                    .rowNumber(result.line())
                    .rawCells(workbook.rows().get(index).values())
                    .status(ImportRowStatus.PENDING)
                    .build();
            if (result.valid()) {
                row.markValid(result.values());
            } else {
                row.markInvalid(result.values(), List.copyOf(result.issues()));
            }
            rows.add(row);
        }
        rowRepository.saveAll(rows);
        return mapper.toBatchResponse(batch, rows);
    }

    /**
     * History of the caller's own batches (FE Q13). Items appear only when a plant is given and the
     * caller holds the item pair there; suppliers/customers only while the caller holds that pair in
     * the company. Newest first, id as tie-break.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@dataImportBatchGuard.canList(authentication, #kind, #companyId, #plantId)")
    public PageResult<DataImportBatchSummaryResponse> list(UUID companyId, UUID plantId, DataImportKind kind,
                                                           ImportRunStatus status, String search,
                                                           Pageable pageable) {
        if (plantId != null) {
            Plant plant = organizationLookupService.getActivePlant(plantId);
            ensurePlantOfCompany(plant, companyId);
        }
        Set<DataImportKind> allowed = batchGuard.allowedKinds(
                SecurityContextHolder.getContext().getAuthentication(), companyId, plantId);
        if (kind != null) {
            allowed.retainAll(Set.of(kind));
        }
        if (allowed.isEmpty()) {
            return PageResult.from(new PageImpl<>(List.of(), pageable, 0));
        }
        Page<ImportRun> page = runRepository.searchBatches(currentUser(), companyId, plantId,
                allowed.contains(DataImportKind.ITEMS), allowed.contains(DataImportKind.SUPPLIERS),
                allowed.contains(DataImportKind.CUSTOMERS), allowed.contains(DataImportKind.BOMS),
                allowed.contains(DataImportKind.SALES_ORDERS), status,
                StringUtils.hasText(search) ? search.trim() : null, pageable);
        return PageResult.from(page.map(mapper::toBatchSummary));
    }

    /** Detail with every row and issue. Another owner's batch is a 404, never a 403 (FE §1.4). */
    @Transactional(readOnly = true)
    @PreAuthorize("@dataImportBatchGuard.canAccessBatch(authentication, #batchId)")
    public DataImportBatchResponse get(UUID batchId) {
        UUID owner = currentUser();
        ImportRun batch = runRepository.findByImportRunIdAndKind(batchId, ImportRunKind.FIXED_TEMPLATE)
                .filter(run -> owner.equals(run.getOwnerUserId()))
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Import batch", batchId));
        return mapper.toBatchResponse(batch,
                rowRepository.findByImportRunImportRunIdOrderByRowNumberAsc(batchId));
    }

    /**
     * Commit, all or nothing. Order matters and follows FE §3.6: authorisation (the guard, on every
     * call), then the key check, then — for a batch already committed — the stored receipt, before any
     * version check, so a retry after a lost response never fails on its own stale version.
     *
     * <p>Deliberately not {@code @Transactional}: the commit and the "mark INVALID" that follows a
     * stale commit are two transactions in {@link DataImportBatchCommitter}, so the second survives the
     * rollback of the first.
     */
    @PreAuthorize("@dataImportBatchGuard.canAccessBatch(authentication, #batchId)")
    public DataImportBatchResponse commit(UUID batchId, long expectedVersion, String idempotencyKey) {
        UUID owner = currentUser();
        String key = StringUtils.hasText(idempotencyKey) ? idempotency.normalizeKey(idempotencyKey) : null;
        Optional<DataImportBatchResponse> receipt =
                committer.findCommittedReceipt(batchId, owner, key, expectedVersion);
        if (receipt.isPresent()) {
            return receipt.get();
        }
        try {
            return committer.commit(batchId, owner, expectedVersion, key);
        } catch (DataImportBatchCommitter.MasterWriteFailedException failure) {
            throw staleOr(batchId, owner, failure.failure());
        } catch (AppException failure) {
            if (failure.getErrorCode() != BusinessErrorCode.IMPORT_REVALIDATION_FAILED) {
                throw failure;
            }
            throw staleOr(batchId, owner, failure);
        }
    }

    private RuntimeException staleOr(UUID batchId, UUID owner, RuntimeException original) {
        return committer.markInvalidIfStale(batchId, owner)
                .<RuntimeException>map(invalid -> new ResultCarryingException(
                        BusinessErrorCode.IMPORT_REVALIDATION_FAILED,
                        "Import batch is no longer valid; nothing was created", invalid))
                .orElse(original);
    }

    private String checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ExceptionFactory.custom(ValidationErrorCode.FILE_FORMAT, "An .xlsx file is required");
        }
        String fileName = cleanFilename(file.getOriginalFilename());
        String contentType = file.getContentType();
        boolean xlsxName = fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx");
        // Browsers and HTTP clients disagree on the content type of an .xlsx; the archive itself is
        // inspected by the reader, so only a declared type that is plainly something else is refused.
        boolean acceptableType = contentType == null || contentType.isBlank()
                || XLSX_CONTENT_TYPE.equals(contentType) || "application/octet-stream".equals(contentType);
        if (!xlsxName || !acceptableType) {
            throw ExceptionFactory.custom(ValidationErrorCode.FILE_FORMAT,
                    "Only .xlsx workbooks are accepted (not .xls, .xlsm or .csv)");
        }
        if (file.getSize() > properties.fixedTemplateMaxFileBytes()) {
            throw ExceptionFactory.custom(ValidationErrorCode.FILE_SIZE,
                    "File exceeds " + properties.fixedTemplateMaxFileBytes() + " bytes");
        }
        return fileName;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw ExceptionFactory.custom(ValidationErrorCode.FILE_FORMAT, "Uploaded file could not be read");
        }
    }

    private Plant activePlantOf(Company company, UUID plantId) {
        Plant plant = organizationLookupService.getActivePlant(plantId);
        ensurePlantOfCompany(plant, company.getCompanyId());
        return plant;
    }

    private void ensurePlantOfCompany(Plant plant, UUID companyId) {
        if (!plant.getCompany().getCompanyId().equals(companyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Plant does not belong to company");
        }
    }

    private UUID currentUser() {
        return auditorAware.getCurrentAuditor()
                .orElseThrow(() -> new AccessDeniedException("An authenticated user is required"));
    }

    private String cleanFilename(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null || filename.isBlank() ? "upload.xlsx" : filename);
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        cleaned = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(cleaned.length() - 255);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** What makes two uploads "the same" for an Idempotency-Key (FE §3.6). */
    private record UploadFingerprint(String sha256, String fileName, String kind, UUID companyId, UUID plantId) {
    }
}
