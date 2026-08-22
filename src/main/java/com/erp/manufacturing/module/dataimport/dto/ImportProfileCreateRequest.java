package com.erp.manufacturing.module.dataimport.dto;

import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Creates a mapping profile.
 *
 * <p>{@code companyId} is required even though the column is nullable: a profile shared across every
 * company is a deployment decision (it is how the shipped default for our own template is seeded),
 * not something an API caller should be able to do by omitting a field.
 *
 * <p>Row indexes are 0-based to match POI and the stored columns. The API does not translate them to
 * Excel's 1-based numbering, because the mapping screen sets them from a preview it renders itself.
 */
public record ImportProfileCreateRequest(

        @NotBlank
        @Size(max = 100)
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        ImportTargetType targetType,

        @NotNull
        UUID companyId,

        @Size(max = 255)
        String sheetName,

        @PositiveOrZero
        Integer headerRowIndex,

        @PositiveOrZero
        Integer firstDataRowIndex,

        @NotEmpty
        List<@Valid ColumnMappingRequest> mappings) {
}
