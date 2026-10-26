package com.erp.manufacturing.module.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code POST /v1/sales-invoices/{id}/cancel} (G10c): a DRAFT only; the reason is 5 to 2000 characters after trim. */
public record SalesInvoiceCancelRequest(@NotNull Long expectedVersion, @NotBlank String reason) {}
