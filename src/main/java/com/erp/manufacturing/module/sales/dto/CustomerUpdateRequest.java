package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial update. For every field: {@code null} (or omitted) keeps the current value. For the optional
 * text fields a blank string clears the value to {@code null}; {@code name} is required and cannot be
 * cleared. {@code code}, the owning company and the status are not updatable here by design.
 */
public record CustomerUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String contactName,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String phone,
        @Size(max = 500) String address,
        @Size(max = 500) String notes
) {}
