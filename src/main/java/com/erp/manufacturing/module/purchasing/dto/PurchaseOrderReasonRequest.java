package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /{id}/close} (required) and {@code POST /{id}/cancel} (optional — without a body
 * cancel keeps its legacy DRAFT-only behaviour). G7a.
 */
public record PurchaseOrderReasonRequest(
        @NotNull Long expectedVersion,
        @NotBlank @Size(max = 500) String reason
) {}
