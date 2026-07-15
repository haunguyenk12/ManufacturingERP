package com.erp.manufacturing.module.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlantCreateRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[A-Z0-9._-]+$", message = "Code must contain uppercase letters, digits, dots, underscores or hyphens")
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 100)
        String timezone
) {}
