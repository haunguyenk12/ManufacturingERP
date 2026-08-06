package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One lot's balance in one warehouse (C2-2) — list rows and the status-change response share this
 * shape. {@code manufactureDate} is an alias of {@link com.erp.manufacturing.module.inventory.domain.
 * InventoryLot#getReceivedAt()}, not a separate column (same pattern as {@code bomCapturedAt}, F8).
 * {@code sourceMovementType}/{@code sourceReferenceType}/{@code sourceReferenceId}/{@code sourceAt}
 * are derived from the lot's earliest {@code RECEIVE} movement and may all be {@code null} if none
 * is found.
 */
public record InventoryLotResponse(
        UUID lotId,
        UUID itemId,
        String itemCode,
        String itemName,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        String lotCode,
        String status,
        BigDecimal onHandQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        Instant manufactureDate,
        Instant expiresAt,
        String sourceMovementType,
        String sourceReferenceType,
        String sourceReferenceId,
        Instant sourceAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
