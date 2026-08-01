package com.erp.manufacturing.module.planning.dto;

import jakarta.validation.constraints.NotNull;

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
        UUID warehouseId,
        @NotNull LocalDate horizonStartDate,
        @NotNull LocalDate horizonEndDate,
        List<UUID> demandLineIds
) {}
