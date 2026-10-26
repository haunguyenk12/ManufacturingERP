package com.erp.manufacturing.module.billing.dto;

import jakarta.validation.constraints.NotNull;

/** {@code POST /v1/sales-invoices/{id}/post} (G10c). */
public record SalesInvoicePostRequest(@NotNull Long expectedVersion) {}
