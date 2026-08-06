package com.erp.manufacturing.module.inventory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /inventory/lots/{lotId}} (C2-2). No single {@code warehouseId} — a lot can hold stock
 * in more than one warehouse (see {@code InventoryLotResponse} javadoc) — so quantities are broken
 * out per warehouse in {@link #balances()} instead of being flattened onto this record.
 */
public record InventoryLotDetailResponse(
        UUID lotId,
        UUID itemId,
        String itemCode,
        String itemName,
        String lotCode,
        String status,
        Instant manufactureDate,
        Instant expiresAt,
        String sourceMovementType,
        String sourceReferenceType,
        String sourceReferenceId,
        Instant sourceAt,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        List<InventoryLotBalanceResponse> balances
) {}
