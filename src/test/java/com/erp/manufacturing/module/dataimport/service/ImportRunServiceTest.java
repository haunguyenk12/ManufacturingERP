package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.config.DataImportProperties;
import com.erp.manufacturing.module.dataimport.domain.ColumnMapping;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportProfile;
import com.erp.manufacturing.module.dataimport.domain.ImportProfileStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRowStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportProfileRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRowRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.dataimport.spreadsheet.SpreadsheetReader;
import com.erp.manufacturing.module.dataimport.target.ImportFieldDescriptor;
import com.erp.manufacturing.module.dataimport.target.ImportTargetDescriptor;
import com.erp.manufacturing.module.dataimport.target.ImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportRunServiceTest {

    @Mock ImportRunRepository runRepository;
    @Mock ImportRowRepository rowRepository;
    @Mock ImportProfileRepository profileRepository;
    @Mock ImportTargetRegistry registry;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ImportRowApplyService rowApplyService;
    @Mock ImportTargetHandler handler;
    @Mock AuditLogService auditLogService;

    private ImportRunService service;
    private UUID companyId;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new ImportRunService(runRepository, rowRepository, profileRepository,
                new SpreadsheetReader(), registry, organizationLookupService, rowApplyService,
                new IdempotencySupport(new ObjectMapper().findAndRegisterModules()),
                new DataImportProperties(5_000), new DataImportMapper(), auditLogService);
        companyId = UUID.randomUUID();
        company = Company.builder().companyId(companyId).code("CO").name("Company")
                .status(OrganizationStatus.ACTIVE).build();
    }

    @Test
    void upload_stagesRawRowsAndUnknownColumnsWithoutApplyingAnything() throws Exception {
        when(registry.handlerFor(ImportTargetType.ITEM)).thenReturn(handler);
        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(runRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ImportRun run = invocation.getArgument(0);
            run.setImportRunId(UUID.randomUUID());
            return run;
        });
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "vật-tư.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook());

        var response = service.upload(file, ImportTargetType.ITEM, null, companyId, null, null);

        assertThat(response.status()).isEqualTo("PARSED");
        assertThat(response.detectedHeaders()).containsExactly("Mã vật tư", "Cột lạ");
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.fileSha256()).hasSize(64);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImportRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(rowRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo(ImportRowStatus.PENDING);
            assertThat(row.getRawCells()).containsEntry("Cột lạ", "vẫn lưu");
        });
        verifyNoInteractions(rowApplyService);
    }

    @Test
    void upload_rejectsWrongContentTypeBeforeReadingWorkbook() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "items.xlsx", "text/plain", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(
                file, ImportTargetType.ITEM, null, companyId, null, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Only .xlsx files");
        verifyNoInteractions(runRepository, rowRepository, organizationLookupService);
    }

    @Test
    void validate_marksEveryRowAndPersistsBatchErrors() {
        UUID runId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        ImportRun run = run(runId, ImportRunStatus.PARSED);
        ImportProfile profile = profile(profileId);
        List<ImportRow> rows = List.of(
                row(run, 2, "VT-001"), row(run, 3, "VT-001"));
        when(runRepository.findWithDetailsByImportRunId(runId)).thenReturn(Optional.of(run));
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(registry.handlerFor(ImportTargetType.ITEM)).thenReturn(handler);
        when(handler.descriptor()).thenReturn(descriptor());
        when(handler.validateBatch(eq(companyId), any())).thenReturn(Map.of(1, List.of(
                ImportCellError.of("Mã", "code", ImportErrorCode.DUPLICATE_IN_FILE, "duplicate"))));
        when(rowRepository.findByImportRunImportRunIdOrderByRowNumberAsc(runId)).thenReturn(rows);
        when(runRepository.save(run)).thenReturn(run);

        var response = service.validate(runId, profileId);

        assertThat(response.status()).isEqualTo("VALIDATED");
        assertThat(response.validRows()).isEqualTo(1);
        assertThat(response.errorRows()).isEqualTo(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(ImportRowStatus.VALID);
        assertThat(rows.get(1).getStatus()).isEqualTo(ImportRowStatus.ERROR);
        assertThat(rows.get(1).getErrors()).extracting(ImportCellError::code)
                .containsExactly(ImportErrorCode.DUPLICATE_IN_FILE);
        verify(handler).validateBatch(eq(companyId), any());
        verify(rowRepository).saveAll(rows);
    }

    @Test
    void apply_continuesAfterOneRowFailsAndEndsPartiallyApplied() {
        UUID runId = UUID.randomUUID();
        ImportRun run = run(runId, ImportRunStatus.VALIDATED);
        ImportRow first = row(run, 2, "VT-001");
        first.setImportRowId(UUID.randomUUID());
        first.markValid(Map.of("code", "VT-001"));
        ImportRow second = row(run, 3, "VT-002");
        second.setImportRowId(UUID.randomUUID());
        second.markValid(Map.of("code", "VT-002"));
        when(runRepository.findWithDetailsByImportRunId(runId)).thenReturn(Optional.of(run));
        when(rowRepository.findByImportRunImportRunIdAndStatusOrderByRowNumberAsc(
                runId, ImportRowStatus.VALID)).thenReturn(List.of(first, second));
        when(runRepository.saveAndFlush(run)).thenReturn(run);
        when(runRepository.save(run)).thenReturn(run);
        when(rowApplyService.apply(first.getImportRowId())).thenReturn(UUID.randomUUID());
        when(rowApplyService.apply(second.getImportRowId())).thenThrow(new IllegalStateException("duplicate"));

        var response = service.apply(runId, null);

        assertThat(response.status()).isEqualTo("PARTIALLY_APPLIED");
        assertThat(response.appliedRows()).isEqualTo(1);
        assertThat(response.failedRows()).isEqualTo(1);
        verify(rowApplyService).markFailed(second.getImportRowId(), "duplicate");
    }

    @Test
    void apply_refusesRunThatHasNotBeenValidated() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findWithDetailsByImportRunId(runId))
                .thenReturn(Optional.of(run(runId, ImportRunStatus.PARSED)));

        assertThatThrownBy(() -> service.apply(runId, "key-1"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("cannot be applied");
        verify(rowRepository, never()).findByImportRunImportRunIdAndStatusOrderByRowNumberAsc(any(), any());
        verifyNoInteractions(rowApplyService);
    }

    private ImportRun run(UUID runId, ImportRunStatus status) {
        return ImportRun.builder().importRunId(runId).code("IMP-TEST").targetType(ImportTargetType.ITEM)
                .company(company).originalFilename("items.xlsx").fileSizeBytes(100L)
                .fileSha256("a".repeat(64)).detectedHeaders(List.of("Mã", "Tên", "Loại", "ĐVT"))
                .status(status).totalRows(2).build();
    }

    private ImportProfile profile(UUID profileId) {
        return ImportProfile.builder().profileId(profileId).code("ITEM").name("Item")
                .targetType(ImportTargetType.ITEM).company(company).headerRowIndex(0).firstDataRowIndex(1)
                .status(ImportProfileStatus.ACTIVE).mappings(List.of(
                        new ColumnMapping("Mã", "code", List.of("TRIM", "UPPER"), null),
                        new ColumnMapping("Tên", "name", List.of("TRIM"), null),
                        new ColumnMapping("Loại", "type", List.of("TRIM", "UPPER"), null),
                        new ColumnMapping("ĐVT", "unit", List.of("TRIM", "UPPER"), null),
                        new ColumnMapping("Lô", "lotTracked", List.of("BOOLEAN_VN"), "false"),
                        new ColumnMapping("Serial", "serialTracked", List.of("BOOLEAN_VN"), "false")))
                .build();
    }

    private ImportTargetDescriptor descriptor() {
        return new ImportTargetDescriptor(ImportTargetType.ITEM, "Item", List.of(
                ImportFieldDescriptor.string("code", "Code", true, 100, null),
                ImportFieldDescriptor.string("name", "Name", true, 255, null),
                ImportFieldDescriptor.enumeration("type", "Type", true, List.of("RAW_MATERIAL"), null),
                ImportFieldDescriptor.string("unit", "Unit", true, 30, null),
                ImportFieldDescriptor.bool("lotTracked", "Lot", null),
                ImportFieldDescriptor.bool("serialTracked", "Serial", null)));
    }

    private ImportRow row(ImportRun run, int number, String code) {
        return ImportRow.builder().importRun(run).rowNumber(number).status(ImportRowStatus.PENDING)
                .rawCells(Map.of("Mã", code, "Tên", "Khung", "Loại", "RAW_MATERIAL", "ĐVT", "CAI",
                        "Lô", "Không", "Serial", "Không"))
                .build();
    }

    private byte[] workbook() throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã vật tư");
            header.createCell(1).setCellValue("Cột lạ");
            var data = sheet.createRow(1);
            data.createCell(0).setCellValue("007");
            data.createCell(1).setCellValue("vẫn lưu");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
