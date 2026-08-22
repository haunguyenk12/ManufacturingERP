package com.erp.manufacturing.module.dataimport.mapper;

import com.erp.manufacturing.module.dataimport.domain.ImportProfile;
import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
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
import java.util.Set;

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
