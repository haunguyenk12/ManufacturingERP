package com.erp.manufacturing.module.workcenter.dto;

import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * {@code workCalendarId} is optional (C2-7 Part C) — {@code null} means "no calendar assigned",
 * a valid state for a work center that doesn't run on a shift pattern.
 */
public record WorkCenterCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull CapacityUnitType capacityUnitType,
        @NotNull @Positive Integer capacityUnits,
        UUID workCalendarId
) {}
