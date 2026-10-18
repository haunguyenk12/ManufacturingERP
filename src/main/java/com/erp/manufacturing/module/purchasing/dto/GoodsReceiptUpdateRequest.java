package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code PATCH /v1/goods-receipts/{id}} (G7b): DRAFT only, full replacement of the lines with stable
 * line ids. {@code reference}/{@code notes}: {@code null} keeps, empty clears.
 */
public record GoodsReceiptUpdateRequest(
        @NotNull Long expectedVersion,
        @NotNull LocalDate receivedDate,
        @Size(max = 200) String reference,
        @Size(max = 2000) String notes,
        @NotNull @Size(min = 1, max = 500) List<@Valid @NotNull GoodsReceiptDraftLineRequest> lines
) {}
