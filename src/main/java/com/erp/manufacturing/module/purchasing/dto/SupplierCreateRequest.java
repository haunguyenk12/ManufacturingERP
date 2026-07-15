package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SupplierCreateRequest(
        @NotNull UUID companyId,
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 60) String phone,
        @Size(max = 4000) String address,
        @Size(max = 100) String taxCode
) {}
