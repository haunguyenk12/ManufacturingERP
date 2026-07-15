package com.erp.manufacturing.module.organization.dto;

import com.erp.manufacturing.module.organization.domain.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WarehouseUpdateRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        WarehouseType type
) {}
