package com.erp.manufacturing.module.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PermissionCreateRequest(
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "^[A-Z0-9_.:-]+$", message = "Code must be uppercase and machine-readable")
        String code,

        @NotBlank
        @Size(max = 80)
        String resource,

        @NotBlank
        @Size(max = 80)
        String action,

        String description
) {}
