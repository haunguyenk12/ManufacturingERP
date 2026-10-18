package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of {@code POST /v1/purchase-orders/drafts} or {@code PATCH /v1/purchase-orders/{id}} (G7a).
 * {@code id} keeps an existing line on PATCH (stable line ids, FE Q3) and must be omitted on create.
 * {@code unitPrice} may stay {@code null} while DRAFT — explicit {@code 0} is a price, {@code null} is
 * "not priced yet" (FE Q4); confirm requires every line to have one. UOM and currency are never sent.
 * {@code @Digits(13, 6)} matches {@code NUMERIC(19,6)}: a seventh decimal is a 400, not a round.
 */
public record PurchaseOrderDraftLineRequest(
        UUID id,
        @NotNull UUID itemId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal orderedQuantity,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal unitPrice,
        @NotNull LocalDate expectedDate
) {}
