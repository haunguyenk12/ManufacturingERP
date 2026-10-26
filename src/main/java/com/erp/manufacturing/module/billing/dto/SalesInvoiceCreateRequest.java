package com.erp.manufacturing.module.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /v1/sales-invoices} (G10c): a DRAFT for one order. {@code billingSnapshotVersion} is the one the preview
 * returned for these lines; a different base answers {@code PRICE_STALE} (FE Q22).
 */
public record SalesInvoiceCreateRequest(
        @NotNull UUID salesOrderId,
        @NotNull Long expectedOrderVersion,
        @NotBlank @Size(max = 80) String billingSnapshotVersion,
        @NotNull LocalDate documentDate,
        @NotNull LocalDate dueDate,
        @Size(max = 2000) String note,
        @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull SalesInvoiceLineRequest> lines
) {}
