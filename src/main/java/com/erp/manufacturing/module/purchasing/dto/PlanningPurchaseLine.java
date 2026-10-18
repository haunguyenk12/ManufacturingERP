package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One line of a purchase order drafted from a planning conversion (G6b, D9). */
public record PlanningPurchaseLine(UUID itemId, BigDecimal orderedQuantity, LocalDate expectedDate) {}
