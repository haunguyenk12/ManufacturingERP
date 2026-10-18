package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRowStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchResponse;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportRowRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.dataimport.target.ImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The validator and the mapper are real (R3); the target handler is the mock, because what it does —
 * write to another module — is exactly the side effect each case has to prove did or did not happen.
 */
class DataImportBatchCommitterTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    private final ImportRunRepository runRepository = mock(ImportRunRepository.class);
    private final ImportRowRepository rowRepository = mock(ImportRowRepository.class);
    private final ImportTargetHandler handler = mock(ImportTargetHandler.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final IdempotencySupport idempotency = new IdempotencySupport(new ObjectMapper().findAndRegisterModules());
    private DataImportBatchCommitter committer;
    private UUID batchId;

    @BeforeEach
    void setUp() {
        when(handler.targetType()).thenReturn(ImportTargetType.CUSTOMER);
        when(handler.validateBatch(any(), anyList())).thenReturn(Map.of());
        ImportTargetRegistry registry = new ImportTargetRegistry(List.of(handler));
        committer = new DataImportBatchCommitter(runRepository, rowRepository, registry, org.mockito.Mockito.mock(com.erp.manufacturing.module.dataimport.target.BomImportHandler.class), org.mockito.Mockito.mock(com.erp.manufacturing.module.dataimport.target.SalesOrderImportHandler.class),
                new FixedTemplateValidator(registry, org.mockito.Mockito.mock(com.erp.manufacturing.module.dataimport.target.BomImportHandler.class), org.mockito.Mockito.mock(com.erp.manufacturing.module.dataimport.target.SalesOrderImportHandler.class)), idempotency, new DataImportMapper(), entityManager);
        batchId = UUID.randomUUID();
        when(runRepository.saveAndFlush(any())).then(returnsFirstArg());
        when(runRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    }

    @Test
    void commit_readyBatch_createsEveryRowInFileOrder_thenMarksCommitted() {
        ImportRun batch = batch(ImportRunStatus.READY, 0L);
        List<ImportRow> rows = rows(batch, "C-1", "C-2");
        lock(batch, rows);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(handler.apply(eq(COMPANY_ID), any())).thenReturn(first, second);

        DataImportBatchResponse response = committer.commit(batchId, OWNER, 0L, "K-1");

        InOrder order = inOrder(handler, entityManager, runRepository);
        order.verify(handler).apply(eq(COMPANY_ID), eq(handlerValues("C-1")));
        order.verify(handler).apply(eq(COMPANY_ID), eq(handlerValues("C-2")));
        order.verify(entityManager).flush();
        order.verify(runRepository).saveAndFlush(batch);
        assertThat(response.status()).isEqualTo("COMMITTED");
        assertThat(response.createdIds()).containsExactly(first, second);
        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(batch.getIdempotencyKey()).isEqualTo("K-1");
        // Microsecond precision, so the stored value is exactly the one in this first response.
        assertThat(batch.getCommittedAt().getNano() % 1_000).isZero();
    }

    @Test
    void commit_invalidBatch_isStateConflict_andCreatesNothing() {
        ImportRun batch = batch(ImportRunStatus.INVALID, 0L);
        lock(batch, rows(batch, "C-1"));

        assertCode(() -> committer.commit(batchId, OWNER, 0L, null), BusinessErrorCode.STATE_CONFLICT);
        verify(handler, never()).apply(any(), any());
    }

    @Test
    void commit_staleExpectedVersion_isConcurrentModification_andCreatesNothing() {
        ImportRun batch = batch(ImportRunStatus.READY, 1L);
        lock(batch, rows(batch, "C-1"));

        assertCode(() -> committer.commit(batchId, OWNER, 0L, null), BusinessErrorCode.CONCURRENT_MODIFICATION);
        verify(handler, never()).apply(any(), any());
    }

    /** FE Q7: a committed batch answers with its receipt before the version is even looked at. */
    @Test
    void commit_alreadyCommitted_returnsTheReceipt_evenWithAnOldVersion_andCreatesNothing() {
        ImportRun batch = batch(ImportRunStatus.COMMITTED, 1L);
        List<ImportRow> rows = rows(batch, "C-1");
        rows.get(0).markApplied(UUID.randomUUID());
        lock(batch, rows);

        DataImportBatchResponse response = committer.commit(batchId, OWNER, 0L, null);

        assertThat(response.status()).isEqualTo("COMMITTED");
        assertThat(response.createdIds()).containsExactly(rows.get(0).getCreatedEntityId());
        verify(handler, never()).apply(any(), any());
    }

    @Test
    void commit_keyAlreadyUsedForAnotherBatch_isIdempotencyConflict_notSomeoneElsesReceipt() {
        ImportRun batch = batch(ImportRunStatus.READY, 0L);
        lock(batch, rows(batch, "C-1"));
        ImportRun other = batch(ImportRunStatus.COMMITTED, 1L);
        other.setImportRunId(UUID.randomUUID());
        when(runRepository.findByIdempotencyKey("K-1")).thenReturn(Optional.of(other));

        assertCode(() -> committer.commit(batchId, OWNER, 0L, "K-1"), BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        verify(handler, never()).apply(any(), any());
    }

    /** FE R03/A13/A14: the data changed since validation — nothing is written at all. */
    @Test
    void commit_revalidationFindsAnIssue_throwsRevalidationFailed_beforeAnyWrite() {
        ImportRun batch = batch(ImportRunStatus.READY, 0L);
        lock(batch, rows(batch, "C-1", "C-2"));
        when(handler.validateBatch(eq(COMPANY_ID), anyList())).thenReturn(Map.of(1, List.of(
                ImportCellError.of(null, "code", ImportErrorCode.ALREADY_EXISTS, "taken"))));

        assertCode(() -> committer.commit(batchId, OWNER, 0L, null), BusinessErrorCode.IMPORT_REVALIDATION_FAILED);
        verify(handler, never()).apply(any(), any());
        verify(runRepository, never()).saveAndFlush(any());
    }

    @Test
    void commit_writeFailureOnAnyRow_isWrapped_soTheCallerCanTellItApartFromAStaleBatch() {
        ImportRun batch = batch(ImportRunStatus.READY, 0L);
        lock(batch, rows(batch, "C-1"));
        DataIntegrityViolationException failure = new DataIntegrityViolationException("uk_customers_company_code");
        when(handler.apply(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> committer.commit(batchId, OWNER, 0L, null))
                .isInstanceOf(DataImportBatchCommitter.MasterWriteFailedException.class)
                .satisfies(ex -> assertThat(((DataImportBatchCommitter.MasterWriteFailedException) ex).failure())
                        .isSameAs(failure));
        verify(runRepository, never()).saveAndFlush(any());
    }

    @Test
    void commit_anotherOwnersBatch_isNotFound() {
        ImportRun batch = batch(ImportRunStatus.READY, 0L);
        batch.setOwnerUserId(UUID.randomUUID());
        lock(batch, rows(batch, "C-1"));

        assertCode(() -> committer.commit(batchId, OWNER, 0L, null), ValidationErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void markInvalidIfStale_storesTheNewIssues_andFlipsTheBatchToInvalid() {
        ImportRun batch = batch(ImportRunStatus.READY, 0L);
        List<ImportRow> rows = rows(batch, "C-1", "C-2");
        lock(batch, rows);
        when(handler.validateBatch(eq(COMPANY_ID), anyList())).thenReturn(Map.of(0, List.of(
                ImportCellError.of(null, "code", ImportErrorCode.ALREADY_EXISTS, "taken"))));

        DataImportBatchResponse response = committer.markInvalidIfStale(batchId, OWNER).orElseThrow();

        assertThat(response.status()).isEqualTo("INVALID");
        assertThat(response.errorRowCount()).isEqualTo(1);
        assertThat(response.validRowCount()).isEqualTo(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(ImportRowStatus.ERROR);
        assertThat(rows.get(0).getErrors()).singleElement()
                .satisfies(issue -> assertThat(issue.column()).isEqualTo("A"));
    }

    /** FE §1.4 "Commit lỗi DB: READY → READY": a failure unrelated to the data leaves the batch alone. */
    @Test
    void markInvalidIfStale_dataStillValid_writesNothing() {
        ImportRun batch = batch(ImportRunStatus.READY, 0L);
        lock(batch, rows(batch, "C-1"));

        assertThat(committer.markInvalidIfStale(batchId, OWNER)).isEmpty();
        assertThat(batch.getStatus()).isEqualTo(ImportRunStatus.READY);
        verify(runRepository, never()).saveAndFlush(any());
    }

    private void lock(ImportRun batch, List<ImportRow> rows) {
        when(runRepository.lockFixedTemplateBatch(batchId)).thenReturn(Optional.of(batch));
        when(rowRepository.findByImportRunImportRunIdOrderByRowNumberAsc(batchId)).thenReturn(rows);
    }

    private ImportRun batch(ImportRunStatus status, long version) {
        ImportRun run = ImportRun.builder()
                .importRunId(batchId).kind(ImportRunKind.FIXED_TEMPLATE).ownerUserId(OWNER)
                .targetType(ImportTargetType.CUSTOMER)
                .company(Company.builder().companyId(COMPANY_ID).code("CO").name("Co")
                        .status(OrganizationStatus.ACTIVE).build())
                .originalFilename("customers.xlsx").fileSizeBytes(1L).fileSha256("a").detectedHeaders(List.of())
                .locale("en").templateVersion("omniplant-import-v1").status(status).build();
        run.setVersion(version);
        run.setTotalRows(1);
        return run;
    }

    private List<ImportRow> rows(ImportRun batch, String... codes) {
        batch.setTotalRows(codes.length);
        batch.setValidRows(codes.length);
        List<ImportRow> rows = new java.util.ArrayList<>();
        for (int index = 0; index < codes.length; index++) {
            ImportRow row = ImportRow.builder().importRun(batch).rowNumber(index + 2)
                    .rawCells(Map.of()).status(ImportRowStatus.PENDING).build();
            row.markValid(values(codes[index]));
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, String> values(String code) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("code", code);
        values.put("name", "Customer " + code);
        values.put("contact", "");
        values.put("email", "");
        values.put("phone", "");
        values.put("address", "");
        values.put("notes", "");
        return values;
    }

    /** FE Q9: empty optional cells reach the master service as null. */
    private static Map<String, String> handlerValues(String code) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("code", code);
        values.put("name", "Customer " + code);
        values.put("contactName", null);
        values.put("email", null);
        values.put("phone", null);
        values.put("address", null);
        values.put("notes", null);
        return values;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
