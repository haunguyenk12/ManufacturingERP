package com.erp.manufacturing.module.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /v1/sales-invoices/preview} (G10c): prices the lines on today's billing base. Holds nothing (FE §3.5);
 * {@code editingInvoiceId} excludes that draft's own claim from the quota check.
 */
public record SalesInvoicePreviewRequest(
        @NotNull UUID salesOrderId,
        @NotNull Long expectedOrderVersion,
        UUID editingInvoiceId,
        @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull SalesInvoiceLineRequest> lines
) {}
