package com.erp.manufacturing.module.workcenter.dto;

import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WorkCenterCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull CapacityUnitType capacityUnitType,
        @NotNull @Positive Integer capacityUnits
) {}
