package com.erp.manufacturing.module.sales.dto;

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
 */
public record SalesOrderDraftLineRequest(
        @NotNull UUID itemId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal orderedQuantity,
        @NotNull LocalDate dueDate
) {}
