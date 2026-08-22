package com.erp.manufacturing.module.inventory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /inventory/lots/{lotId}} (C2-2). A lot can hold stock in more than one warehouse.
 * When the optional {@code warehouseId} query parameter is present, {@link #balances()} contains
 * only that authorized warehouse; company/global/admin callers may omit it to receive every row.
 *
 * <p>{@code receivedAt} is the authoritative "date received"; {@code manufactureDate} is a legacy
 * alias of the same value and {@code createdAt} is a row audit timestamp. See
 * {@link InventoryLotResponse} for why the three must not be used interchangeably.
 */
public record InventoryLotDetailResponse(
        UUID lotId,
        UUID itemId,
        String itemCode,
        String itemName,
        String lotCode,
        String status,
        Instant receivedAt,
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
