package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code PATCH /v1/purchase-orders/{id}} (G7a): DRAFT only. {@code lines} is the full new set — a line
 * sent with its {@code id} is kept and updated, one without an {@code id} is added, one left out is
 * removed. {@code reference}/{@code notes}: {@code null} keeps, empty clears (same rule as every other
 * PATCH, B136). An order that still holds planning quota keeps its warehouse, items, quantities and
 * line set (FE Q7, {@code SOURCE_LOCKED}).
 */
public record PurchaseOrderUpdateRequest(
        @NotNull Long expectedVersion,
        @NotNull UUID supplierId,
        @NotNull UUID warehouseId,
        @NotNull LocalDate orderDate,
        @Size(max = 200) String reference,
        @Size(max = 2000) String notes,
        @NotNull @Size(min = 1, max = 500) List<@Valid @NotNull PurchaseOrderDraftLineRequest> lines
) {}
