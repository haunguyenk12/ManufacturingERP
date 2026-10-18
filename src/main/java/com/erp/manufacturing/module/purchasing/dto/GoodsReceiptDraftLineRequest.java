package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of a goods receipt draft (G7b). Several lines may name the same purchase order line —
 * one per lot. {@code id} keeps an existing line on PATCH and must be omitted on create. Supplier,
 * warehouse, item, UOM and price are never sent: the server takes them from the purchase order.
 * {@code lotCode} only for lot-tracked items (a new code, FE Q28); {@code expiresOn} only with it.
 */
public record GoodsReceiptDraftLineRequest(
        UUID id,
        @NotNull UUID purchaseOrderLineId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal receivedQuantity,
        @Size(max = 120) String lotCode,
        LocalDate expiresOn
) {}
