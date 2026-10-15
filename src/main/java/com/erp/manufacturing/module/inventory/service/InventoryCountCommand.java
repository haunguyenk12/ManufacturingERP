package com.erp.manufacturing.module.inventory.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Absolute stock count (G4-3). Also the idempotency fingerprint: a retry with the same key and a
 * different counted quantity or version is an {@code IDEMPOTENCY_CONFLICT}, not a second count.
 */
public record InventoryCountCommand(
        UUID itemId,
        UUID warehouseId,
        UUID lotId,
        BigDecimal countedQuantity,
        Long expectedVersion,
        String reason
) {}
