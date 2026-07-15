package com.erp.manufacturing.module.bom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BomCreateRequest(
        @NotNull UUID parentItemId,

        @NotBlank
        @Size(max = 40)
        String revision,

        @Size(max = 1000)
        String description
) {}
