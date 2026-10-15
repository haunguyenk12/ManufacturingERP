package com.erp.manufacturing.module.inventory.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code expiresAt} (G4-4) is omitted from the JSON fingerprint when null, so every payload hash
 * stored before it existed still matches a replay of the same request.
 */
public record InventoryReceiveCommand(
        UUID itemId,
        UUID warehouseId,
        UUID lotId,
        String lotCode,
        BigDecimal quantity,
        String reason,
        String referenceType,
        String referenceId,
        UUID serialId,
        String serialCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant expiresAt
) {}
