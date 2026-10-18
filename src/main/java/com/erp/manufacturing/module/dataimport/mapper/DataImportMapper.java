package com.erp.manufacturing.module.dataimport.mapper;

import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportProfile;
import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchResponse;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchRowResponse;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchSummaryResponse;
import com.erp.manufacturing.module.dataimport.dto.DataImportIssueResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportFieldResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportProfileResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportRowResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportRunResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportTargetResponse;
import com.erp.manufacturing.module.dataimport.target.ImportFieldDescriptor;
import com.erp.manufacturing.module.dataimport.target.ImportTargetDescriptor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class DataImportMapper {

    public ImportProfileResponse toResponse(ImportProfile profile) {
        return new ImportProfileResponse(
                profile.getProfileId(), profile.getCode(), profile.getName(), profile.getTargetType().name(),
                profile.getCompany() == null ? null : profile.getCompany().getCompanyId(),
                profile.getSheetName(), profile.getHeaderRowIndex(), profile.getFirstDataRowIndex(),
                profile.getMappings(), profile.getStatus().name(), profile.getCreatedAt(), profile.getUpdatedAt());
    }

    public ImportRunResponse toResponse(ImportRun run) {
        Set<String> mappedHeaders = run.getProfile() == null
                ? Set.of()
                : run.getProfile().getMappings().stream()
                        .map(mapping -> mapping.sourceHeader())
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> unmappedHeaders = run.getProfile() == null
                ? List.of()
                : run.getDetectedHeaders().stream().filter(header -> !mappedHeaders.contains(header)).toList();
        return new ImportRunResponse(
                run.getImportRunId(), run.getCode(), run.getTargetType().name(),
                run.getProfile() == null ? null : run.getProfile().getProfileId(),
                run.getProfile() == null ? null : run.getProfile().getCode(),
                run.getCompany().getCompanyId(),
                run.getPlant() == null ? null : run.getPlant().getPlantId(),
                run.getWarehouse() == null ? null : run.getWarehouse().getWarehouseId(),
                run.getOriginalFilename(), run.getFileSizeBytes(), run.getFileSha256(),
                run.getDetectedHeaders(), unmappedHeaders, run.getStatus().name(),
                run.getTotalRows(), run.getValidRows(), run.getErrorRows(), run.getAppliedRows(),
                run.getFailedRows(), run.getParsedAt(), run.getValidatedAt(), run.getAppliedAt(),
                run.getErrorMessage(), run.getCreatedAt());
    }

    // ── Fixed-template batches (G3) ────────────────────────────────────────

    /** @param rows every row of the batch, in Excel row order */
    public DataImportBatchResponse toBatchResponse(ImportRun run, List<ImportRow> rows) {
        List<UUID> createdIds = run.getStatus() == ImportRunStatus.COMMITTED
                // distinct: every row of a BOM / sales order group carries the id of the one record it
                // became (G5, G6c)
                ? rows.stream().map(ImportRow::getCreatedEntityId).filter(Objects::nonNull).distinct().toList()
                : List.of();
        return new DataImportBatchResponse(
                run.getImportRunId(), run.getOwnerUserId(), run.getCompany().getCompanyId(),
                run.getPlant() == null ? null : run.getPlant().getPlantId(),
                kindOf(run), run.getLocale(), run.getTemplateVersion(),
                run.getOriginalFilename(), run.getFileSizeBytes(), run.getStatus().name(),
                versionOf(run), run.getCreatedAt(), run.getCommittedAt(),
                run.getTotalRows(), run.getValidRows(), run.getErrorRows(), createdCount(run),
                rows.stream().map(this::toBatchRow).toList(), createdIds,
                groupCount(run, rows, DataImportKind.BOMS, FixedTemplateSchema.BOM_CODE),
                groupCount(run, rows, DataImportKind.SALES_ORDERS, FixedTemplateSchema.GROUP_CODE));
    }

    public DataImportBatchSummaryResponse toBatchSummary(ImportRun run) {
        return new DataImportBatchSummaryResponse(
                run.getImportRunId(), run.getOwnerUserId(), run.getCompany().getCompanyId(),
                run.getPlant() == null ? null : run.getPlant().getPlantId(),
                kindOf(run), run.getLocale(), run.getTemplateVersion(),
                run.getOriginalFilename(), run.getFileSizeBytes(), run.getStatus().name(),
                versionOf(run), run.getCreatedAt(), run.getCommittedAt(),
                run.getTotalRows(), run.getValidRows(), run.getErrorRows(), createdCount(run));
    }

    private DataImportBatchRowResponse toBatchRow(ImportRow row) {
        Map<String, String> values = row.getMappedValues() == null ? Map.of() : row.getMappedValues();
        List<ImportCellError> errors = row.getErrors() == null ? List.of() : row.getErrors();
        return new DataImportBatchRowResponse(row.getRowNumber(), values, errors.stream()
                .map(error -> new DataImportIssueResponse(error.targetField(), error.column(),
                        error.code().name(), error.message()))
                .toList());
    }

    private DataImportKind kindOf(ImportRun run) {
        return DataImportKind.fromTargetType(run.getTargetType())
                .orElseThrow(() -> new IllegalStateException("No import kind for " + run.getTargetType()));
    }

    /** Distinct non-blank values of {@code field} — what a commit of a grouped kind creates; else {@code null}. */
    private Integer groupCount(ImportRun run, List<ImportRow> rows, DataImportKind groupedKind, String field) {
        if (kindOf(run) != groupedKind) {
            return null;
        }
        return (int) rows.stream()
                .map(row -> row.getMappedValues() == null ? null : row.getMappedValues().get(field))
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .count();
    }

    private long versionOf(ImportRun run) {
        return run.getVersion() == null ? 0L : run.getVersion();
    }

    private int createdCount(ImportRun run) {
        return run.getStatus() == ImportRunStatus.COMMITTED ? run.getAppliedRows() : 0;
    }

    public ImportRowResponse toResponse(ImportRow row) {
        return new ImportRowResponse(row.getImportRowId(), row.getRowNumber(), row.getStatus().name(),
                row.getRawCells(), row.getMappedValues(), row.getErrors(), row.getCreatedEntityId());
    }

    public ImportTargetResponse toResponse(ImportTargetDescriptor descriptor) {
        return new ImportTargetResponse(descriptor.type().name(), descriptor.label(),
                descriptor.fields().stream().map(this::toResponse).toList());
    }

    private ImportFieldResponse toResponse(ImportFieldDescriptor field) {
        return new ImportFieldResponse(field.name(), field.label(), field.type().name(), field.required(),
                field.maxLength(), field.allowedValues(), field.example());
    }
}
