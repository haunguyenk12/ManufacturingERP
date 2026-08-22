package com.erp.manufacturing.module.inventory.dto;

import com.erp.manufacturing.module.inventory.domain.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record ItemCreateRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[A-Z0-9._-]+$", message = "Code must contain uppercase letters, digits, dots, underscores or hyphens")
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        ItemType type,

        @NotBlank
        @Size(max = 30)
        String unit,

        boolean lotTracked,

        @Schema(hidden = true) boolean serialTracked
) {}
