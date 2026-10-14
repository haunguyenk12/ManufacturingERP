package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /v1/companies/{companyId}/suppliers} (G2-2). Same fields and limits as
 * {@link SupplierCreateRequest} minus {@code companyId}, which comes from the path — the shape FE
 * §3.4 asked for. The older {@code POST /v1/suppliers} keeps {@link SupplierCreateRequest} unchanged.
 */
public record CompanySupplierCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 60) String phone,
        @Size(max = 4000) String address,
        @Size(max = 100) String taxCode,
        @Size(max = 255) String contactName,
        @Size(max = 500) String notes
) {}
