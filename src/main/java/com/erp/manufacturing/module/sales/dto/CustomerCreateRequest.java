package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code code} carries only {@code @NotBlank}: its pattern and length are checked by
 * {@code CustomerService} <em>after</em> trim + uppercase, so {@code " partner-01 "} is accepted as
 * {@code PARTNER-01} instead of being rejected for its padding or case.
 */
public record CustomerCreateRequest(
        @NotBlank String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String contactName,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String phone,
        @Size(max = 500) String address,
        @Size(max = 500) String notes
) {}
