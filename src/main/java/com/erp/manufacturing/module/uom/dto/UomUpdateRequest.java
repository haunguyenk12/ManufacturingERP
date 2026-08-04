package com.erp.manufacturing.module.uom.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code code} is deliberately absent — it is immutable after creation (spec §3.1). A field simply
 * not existing on this DTO is the contract, not a runtime check: there is no value to reject.
 */
public record UomUpdateRequest(
        @Size(max = 100) String name,
        String description
) {}
