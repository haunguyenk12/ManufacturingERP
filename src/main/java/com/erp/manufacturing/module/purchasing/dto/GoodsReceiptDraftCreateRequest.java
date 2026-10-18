package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /v1/goods-receipts} (G7b): a DRAFT receipt against a CONFIRMED/PARTIALLY_RECEIVED
 * purchase order, numbered by the server ({@code GR-000001}…). Saving moves no stock and reserves
 * nothing on the order; posting re-checks everything under a lock.
 */
public record GoodsReceiptDraftCreateRequest(
        @NotNull UUID purchaseOrderId,
        @NotNull LocalDate receivedDate,
        @Size(max = 200) String reference,
        @Size(max = 2000) String notes,
        @NotNull @Size(min = 1, max = 500) List<@Valid @NotNull GoodsReceiptDraftLineRequest> lines
) {}
