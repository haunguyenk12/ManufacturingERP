package com.erp.manufacturing.module.planning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /v1/planning-runs/{runId}/conversions} (G6b). All entries are MAKE (one draft work
 * order each) or all BUY (one draft purchase order, {@code supplierId} required, one receiving
 * warehouse).
 */
public record PlanningConversionRequest(
        @NotBlank String expectedSourceVersion,
        @NotNull @PositiveOrZero Integer expectedConversionVersion,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull PlanningConversionEntryRequest> entries,
        UUID supplierId
) {}
