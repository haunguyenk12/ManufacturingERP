package com.erp.manufacturing.module.planning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /v1/planning-runs/commercial} (G6b, D6). {@code salesOrderLineIds} are sales order line
 * ids — not planning demand ids, which is what {@code demandLineIds} of the standard run means.
 * The period is at most 90 days, both ends included. {@code includeRop = true} is refused: the
 * item-warehouse settings carry no reorder quantity, so a reorder-point lane cannot be computed.
 */
public record CommercialPlanningRunRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        @NotNull LocalDate horizonStartDate,
        @NotNull LocalDate horizonEndDate,
        @NotEmpty @Size(max = 200) List<@NotNull UUID> salesOrderLineIds,
        @Size(max = 500) List<@Valid @NotNull ItemWarehouseMappingRequest> itemWarehouseMappings,
        Boolean includeRop
) {}
