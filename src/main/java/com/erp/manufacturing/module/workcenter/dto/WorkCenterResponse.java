package com.erp.manufacturing.module.workcenter.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Spec §3.4 asks for an explicit "optimistic version" in the response — unlike Sales Order/UOM
 * responses elsewhere in this repo, {@code version} (BaseEntity's optimistic-locking counter) is
 * deliberately exposed here rather than omitted.
 */
public record WorkCenterResponse(
        UUID workCenterId,
        UUID plantId,
        String code,
        String name,
        String description,
        String capacityUnitType,
        Integer capacityUnits,
        String status,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        UUID workCalendarId
) {}
