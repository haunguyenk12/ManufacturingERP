package com.erp.manufacturing.module.bom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BomUpdateRequest(
        @NotBlank
        @Size(max = 40)
        String revision,

        @Size(max = 1000)
        String description
) {}
