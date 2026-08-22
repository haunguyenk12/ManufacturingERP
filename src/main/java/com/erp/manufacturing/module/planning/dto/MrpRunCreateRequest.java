package com.erp.manufacturing.module.planning.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param demandLineIds the planning demands the planner picked from
 *        {@code GET /sales-orders/planning-demands} (spec §2.3). Omitted or empty keeps the original
 *        behaviour of sweeping every OPEN demand inside the horizon, so existing clients are not
 *        broken; when given, only those demands enter the run and the horizon no longer filters them.
 */
public record MrpRunCreateRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        @Schema(deprecated = true, description = "Compatibility alias; use demandWarehouseId")
        UUID warehouseId,
        @NotNull LocalDate horizonStartDate,
        @NotNull LocalDate horizonEndDate,
        List<UUID> demandLineIds,
        UUID demandWarehouseId
) {
    public MrpRunCreateRequest(UUID companyId, UUID plantId, UUID warehouseId,
                               LocalDate horizonStartDate, LocalDate horizonEndDate,
                               List<UUID> demandLineIds) {
        this(companyId, plantId, warehouseId, horizonStartDate, horizonEndDate, demandLineIds, null);
    }

    public UUID effectiveDemandWarehouseId() {
        return demandWarehouseId != null ? demandWarehouseId : warehouseId;
    }
}
