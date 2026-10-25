package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of a draft saved through {@code POST /drafts} or {@code PATCH /{id}/draft} (G6a). The UOM
 * is never sent: it comes from the item master. {@code @Digits(13, 6)} matches
 * {@code NUMERIC(19,6)} — a seventh decimal is a 400, not a silent round (FE A03).
 *
 * <p>G10a: {@code unitPrice} is optional, VND, 0 to 999 999 999 with at most 6 decimals. Absent = no price yet,
 * which is not 0 (FE R02).
 */
public record SalesOrderDraftLineRequest(
        @NotNull UUID itemId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal orderedQuantity,
        @NotNull LocalDate dueDate,
        @DecimalMin("0") @DecimalMax("999999999") @Digits(integer = 9, fraction = 6) BigDecimal unitPrice
) {
    /** Shape before G10a: no price. */
    public SalesOrderDraftLineRequest(UUID itemId, BigDecimal orderedQuantity, LocalDate dueDate) {
        this(itemId, orderedQuantity, dueDate, null);
    }
}
