package com.erp.manufacturing.module.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** {@code PATCH /v1/sales-invoices/{id}} (G10c): full replacement of a DRAFT, priced again on today's base. */
public record SalesInvoiceUpdateRequest(
        @NotNull Long expectedVersion,
        @NotBlank @Size(max = 80) String billingSnapshotVersion,
        @NotNull LocalDate documentDate,
        @NotNull LocalDate dueDate,
        @Size(max = 2000) String note,
        @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull SalesInvoiceLineRequest> lines
) {}
