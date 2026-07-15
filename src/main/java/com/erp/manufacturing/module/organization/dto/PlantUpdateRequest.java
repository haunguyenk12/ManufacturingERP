package com.erp.manufacturing.module.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlantUpdateRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 100)
        String timezone
) {}
