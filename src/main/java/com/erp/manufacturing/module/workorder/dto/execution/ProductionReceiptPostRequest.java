package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Single-line production receipt (spec §6.3). Collapsed from the previous {@code lines[]} shape in
 * F5 (technical debt #11): a work order has exactly one output item, so the array only ever allowed
 * splitting that item across warehouses within one document — something neither the frontend nor
 * the business flow asks for.
 * <p>
 * The storage table {@code production_receipt_lines} is deliberately kept: collapsing it would be a
 * destructive data migration on historical documents for no behavioural gain. Exactly one line row
 * is written per receipt from F5 onwards.
 */
public record ProductionReceiptPostRequest(
        @NotNull UUID destinationWarehouseId,
        UUID lotId,
        @Size(max = 120) String lotNumber,
        @NotNull @Positive BigDecimal quantity,
        @Size(max = 1000) String reason,
        @Size(max = 2000) String note
) {}
