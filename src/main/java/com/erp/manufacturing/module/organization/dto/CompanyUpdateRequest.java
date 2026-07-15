package com.erp.manufacturing.module.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyUpdateRequest(
        @NotBlank
        @Size(max = 255)
        String name
) {}
