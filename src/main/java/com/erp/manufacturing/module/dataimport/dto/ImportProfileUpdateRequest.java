package com.erp.manufacturing.module.dataimport.dto;

import com.erp.manufacturing.module.dataimport.domain.ImportProfileStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Replaces the editable part of a profile.
 *
 * <p>{@code code}, {@code targetType} and {@code companyId} are absent, which makes them immutable at
 * compile time rather than by a runtime check — the same guarantee {@code UomUpdateRequest} gets by
 * omitting {@code code}. Re-pointing an existing profile at a different target would silently
 * invalidate every mapping in it.
 *
 * <p>{@code mappings} is a full replacement, not a patch: a mapping list is edited as one document on
 * screen, and merging two partial lists would need a stable key this record does not have.
 */
public record ImportProfileUpdateRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 255)
        String sheetName,

        @PositiveOrZero
        Integer headerRowIndex,

        @PositiveOrZero
        Integer firstDataRowIndex,

        @NotEmpty
        List<@Valid ColumnMappingRequest> mappings,

        ImportProfileStatus status) {
}
