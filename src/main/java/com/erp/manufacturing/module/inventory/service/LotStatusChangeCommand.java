package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.LotStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Moves a lot to a new status without moving any quantity. {@code quantity} is recorded on the
 * ledger row for traceability only — stock balances are untouched (spec §7).
 */
public record LotStatusChangeCommand(
        UUID itemId,
        UUID warehouseId,
        UUID lotId,
        LotStatus newStatus,
        BigDecimal quantity,
        String reason,
        String referenceType,
        String referenceId
) {}
