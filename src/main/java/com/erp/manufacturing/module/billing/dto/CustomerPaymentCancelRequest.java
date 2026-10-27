package com.erp.manufacturing.module.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code POST /v1/customer-payments/{id}/cancel} (G10d): a DRAFT only; the reason is 5 to 2000 characters after trim. */
public record CustomerPaymentCancelRequest(@NotNull Long expectedVersion, @NotBlank String reason) {}
