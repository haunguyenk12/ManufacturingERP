package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code lineNo} is deliberately absent: the service assigns 1..N in request order, so the
 * {@code (sales_order_id, line_no)} uniqueness of {@code V28} cannot be violated by a client.
 */
public record SalesOrderLineRequest(
        @NotNull UUID itemId,
        @NotNull @Positive BigDecimal orderedQuantity,
        @NotNull LocalDate dueDate
) {}
