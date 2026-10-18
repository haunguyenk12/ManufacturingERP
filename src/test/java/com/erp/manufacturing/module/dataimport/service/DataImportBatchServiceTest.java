package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.ResultCarryingException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.config.DataImportProperties;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunKind;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchResponse;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportRowRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.dataimport.security.DataImportBatchPermissionGuard;
import com.erp.manufacturing.module.dataimport.spreadsheet.FixedTemplateReader;
import com.erp.manufacturing.module.dataimport.spreadsheet.TemplateWorkbooks;
import com.erp.manufacturing.module.dataimport.target.ImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reader, validator, mapper and idempotency support are real (R3). The target handler is a mock so
 * that "validation never writes a master" is a {@code never().apply(...)} — the side effect that must
 * not happen, observed directly (R5).
 */
class DataImportBatchServiceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportRunRepository runRepository = mock(ImportRunRepository.class);
    private final ImportRowRepository rowRepository = mock(ImportRowRepository.class);
    private final DataImportBatchCommitter committer = mock(DataImportBatchCommitter.class);
    private final OrganizationLookupService organizationLookupService = mock(OrganizationLookupService.class);
    private final ImportTargetHandler handler = mock(ImportTargetHandler.class);
    private final Company company = Company.builder().companyId(COMPANY_ID).code("CO").name("Co")
            .status(OrganizationStatus.ACTIVE).build();
    private DataImportBatchService service;

    @BeforeEach
    void setUp() {
        when(handler.targetType()).thenReturn(ImportTargetType.CUSTOMER);
        service = service(new DataImportProperties(5_000, 0, 0));
        when(handler.validateBatch(any(), anyList())).thenReturn(Map.of());
        when(organizationLookupService.getActiveCompany(COMPANY_ID)).thenReturn(company);
        when(runRepository.saveAndFlush(any())).then(invocation -> {
            ImportRun run = invocation.getArgument(0);
            run.setImportRunId(UUID.randomUUID());
            run.setVersion(0L);
            run.setCreatedAt(Instant.now());
            return run;
        });
    }

    @Test
    void upload_validFile_storesAReadyBatchWithItsRows_andNeverCreatesAMaster() {
        DataImportBatchResponse batch = service.upload(file(customers("C-1", "C-2")), DataImportKind.CUSTOMERS,
                COMPANY_ID, null, null);

        assertThat(batch.status()).isEqualTo("READY");
        assertThat(batch.actorId()).isEqualTo(OWNER);
        assertThat(batch.kind()).isEqualTo(DataImportKind.CUSTOMERS);
        assertThat(batch.locale()).isEqualTo("en");
        assertThat(batch.rowCount()).isEqualTo(2);
        assertThat(batch.createdCount()).isZero();
        assertThat(batch.createdIds()).isEmpty();
        ArgumentCaptor<ImportRun> saved = ArgumentCaptor.forClass(ImportRun.class);
        verify(runRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(ImportRunKind.FIXED_TEMPLATE);
        assertThat(saved.getValue().getOwnerUserId()).isEqualTo(OWNER);
        verify(rowRepository).saveAll(anyList());
        verify(handler, never()).apply(any(), any());
        verifyNoInteractions(committer);
    }

    @Test
    void upload_rowIssues_storeAnInvalidBatchWithTheIssues() {
        when(handler.validateBatch(any(), anyList())).thenReturn(Map.of(1, List.of(
                ImportCellError.of(null, "code", ImportErrorCode.ALREADY_EXISTS, "taken"))));

        DataImportBatchResponse batch = service.upload(file(customers("C-1", "C-2")), DataImportKind.CUSTOMERS,
                COMPANY_ID, null, null);

        assertThat(batch.status()).isEqualTo("INVALID");
        assertThat(batch.errorRowCount()).isEqualTo(1);
        assertThat(batch.validRowCount()).isEqualTo(1);
        assertThat(batch.rows().get(1).issues()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("ALREADY_EXISTS"));
        verify(handler, never()).apply(any(), any());
    }

    /** FE A25: the same key with the same file returns the first batch and stores nothing new. */
    @Test
    void upload_sameKeySameFile_returnsTheFirstBatch() {
        byte[] bytes = customers("C-1");
        DataImportBatchResponse first = service.upload(file(bytes), DataImportKind.CUSTOMERS, COMPANY_ID, null, "K-1");
        ArgumentCaptor<ImportRun> saved = ArgumentCaptor.forClass(ImportRun.class);
        verify(runRepository).saveAndFlush(saved.capture());
        when(runRepository.findByOwnerUserIdAndUploadIdempotencyKey(OWNER, "K-1"))
                .thenReturn(Optional.of(saved.getValue()));
        when(rowRepository.findByImportRunImportRunIdOrderByRowNumberAsc(first.id())).thenReturn(List.of());

        DataImportBatchResponse replay = service.upload(file(bytes), DataImportKind.CUSTOMERS, COMPANY_ID, null, "K-1");

        assertThat(replay.id()).isEqualTo(first.id());
        verify(runRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void upload_sameKeyDifferentFile_isIdempotencyConflict() {
        service.upload(file(customers("C-1")), DataImportKind.CUSTOMERS, COMPANY_ID, null, "K-1");
        ArgumentCaptor<ImportRun> saved = ArgumentCaptor.forClass(ImportRun.class);
        verify(runRepository).saveAndFlush(saved.capture());
        when(runRepository.findByOwnerUserIdAndUploadIdempotencyKey(OWNER, "K-1"))
                .thenReturn(Optional.of(saved.getValue()));

        assertCode(() -> service.upload(file(customers("C-2")), DataImportKind.CUSTOMERS, COMPANY_ID, null, "K-1"),
                BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        verify(runRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void upload_aboveTheConfiguredByteLimit_isFileSize_beforeAnythingIsRead() {
        DataImportBatchService small = service(new DataImportProperties(5_000, 500, 10));

        assertCode(() -> small.upload(file(customers("C-1")), DataImportKind.CUSTOMERS, COMPANY_ID, null, null),
                ValidationErrorCode.FILE_SIZE);
        verifyNoInteractions(organizationLookupService, runRepository);
    }

    @Test
    void upload_xlsmOrCsvName_isFileFormat() {
        MockMultipartFile xlsm = new MockMultipartFile("file", "data.xlsm", XLSX, customers("C-1"));

        assertCode(() -> service.upload(xlsm, DataImportKind.CUSTOMERS, COMPANY_ID, null, null),
                ValidationErrorCode.FILE_FORMAT);
        verifyNoInteractions(runRepository);
    }

    @Test
    void upload_itemsWithAPlantOfAnotherCompany_isScopeMismatch() {
        UUID plantId = UUID.randomUUID();
        Company other = Company.builder().companyId(UUID.randomUUID()).build();
        when(organizationLookupService.getActivePlant(plantId))
                .thenReturn(Plant.builder().plantId(plantId).company(other).build());

        assertCode(() -> service.upload(file(customers("C-1")), DataImportKind.ITEMS, COMPANY_ID, plantId, null),
                BusinessErrorCode.RESOURCE_SCOPE_MISMATCH);
        verifyNoInteractions(runRepository);
    }

    /** FE R04: a committed batch is answered from its receipt — commit is never attempted again. */
    @Test
    void commit_receiptAlreadyExists_returnsItWithoutCommitting() {
        UUID batchId = UUID.randomUUID();
        DataImportBatchResponse receipt = response(batchId, "COMMITTED");
        when(committer.findCommittedReceipt(batchId, OWNER, "K-1", 0L)).thenReturn(Optional.of(receipt));

        assertThat(service.commit(batchId, 0L, "K-1")).isSameAs(receipt);
        verify(committer, never()).commit(any(), any(), eq(0L), any());
    }

    @Test
    void commit_staleData_returns409CarryingTheBatchNowInvalid() {
        UUID batchId = UUID.randomUUID();
        when(committer.findCommittedReceipt(batchId, OWNER, null, 0L)).thenReturn(Optional.empty());
        when(committer.commit(batchId, OWNER, 0L, null)).thenThrow(
                new AppException(BusinessErrorCode.IMPORT_REVALIDATION_FAILED, "stale"));
        DataImportBatchResponse invalid = response(batchId, "INVALID");
        when(committer.markInvalidIfStale(batchId, OWNER)).thenReturn(Optional.of(invalid));

        assertThatThrownBy(() -> service.commit(batchId, 0L, null))
                .isInstanceOf(ResultCarryingException.class)
                .satisfies(ex -> {
                    assertThat(((ResultCarryingException) ex).getErrorCode())
                            .isEqualTo(BusinessErrorCode.IMPORT_REVALIDATION_FAILED);
                    assertThat(((ResultCarryingException) ex).getResult()).isSameAs(invalid);
                });
    }

    /** A race lost on the unique index looks like a write failure; revalidation turns it into INVALID. */
    @Test
    void commit_writeFailureFromALostRace_isAlsoReportedAsRevalidationFailed() {
        UUID batchId = UUID.randomUUID();
        when(committer.findCommittedReceipt(batchId, OWNER, null, 0L)).thenReturn(Optional.empty());
        when(committer.commit(batchId, OWNER, 0L, null)).thenThrow(new DataImportBatchCommitter
                .MasterWriteFailedException(new DataIntegrityViolationException("uk_customers_company_code")));
        when(committer.markInvalidIfStale(batchId, OWNER)).thenReturn(Optional.of(response(batchId, "INVALID")));

        assertCode(() -> service.commit(batchId, 0L, null), BusinessErrorCode.IMPORT_REVALIDATION_FAILED);
    }

    /** FE §1.4: a database failure that is not about the data leaves the batch READY and surfaces as is. */
    @Test
    void commit_writeFailureWithStillValidData_rethrowsTheOriginalFailure() {
        UUID batchId = UUID.randomUUID();
        DataIntegrityViolationException failure = new DataIntegrityViolationException("disk full");
        when(committer.findCommittedReceipt(batchId, OWNER, null, 0L)).thenReturn(Optional.empty());
        when(committer.commit(batchId, OWNER, 0L, null))
                .thenThrow(new DataImportBatchCommitter.MasterWriteFailedException(failure));
        when(committer.markInvalidIfStale(batchId, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commit(batchId, 0L, null)).isSameAs(failure);
    }

    @Test
    void commit_otherBusinessErrors_areNotTurnedIntoRevalidation() {
        UUID batchId = UUID.randomUUID();
        when(committer.findCommittedReceipt(batchId, OWNER, null, 0L)).thenReturn(Optional.empty());
        when(committer.commit(batchId, OWNER, 0L, null))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT, "INVALID batch"));

        assertCode(() -> service.commit(batchId, 0L, null), BusinessErrorCode.STATE_CONFLICT);
        verify(committer, never()).markInvalidIfStale(any(), any());
    }

    private DataImportBatchService service(DataImportProperties properties) {
        ImportTargetRegistry registry = new ImportTargetRegistry(List.of(handler));
        return new DataImportBatchService(runRepository, rowRepository, new FixedTemplateReader(),
                new FixedTemplateValidator(registry, org.mockito.Mockito.mock(com.erp.manufacturing.module.dataimport.target.BomImportHandler.class), org.mockito.Mockito.mock(com.erp.manufacturing.module.dataimport.target.SalesOrderImportHandler.class)), committer, mock(DataImportBatchPermissionGuard.class),
                organizationLookupService, new IdempotencySupport(new ObjectMapper().findAndRegisterModules()),
                properties, new DataImportMapper(), () -> Optional.of(OWNER),
                mock(jakarta.persistence.EntityManager.class));
    }

    private static byte[] customers(String... codes) {
        TemplateWorkbooks workbook = TemplateWorkbooks.partners("customers", "en");
        for (int index = 0; index < codes.length; index++) {
            workbook.row(index + 2, codes[index], "Customer " + codes[index]);
        }
        return workbook.build();
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "customers.xlsx", XLSX, bytes);
    }

    private DataImportBatchResponse response(UUID batchId, String status) {
        return new DataImportBatchResponse(batchId, OWNER, COMPANY_ID, null, DataImportKind.CUSTOMERS, "en",
                "omniplant-import-v1", "customers.xlsx", 1, status, 1, Instant.now(), null, 1, 0, 1, 0,
                List.of(), List.of(), null, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
