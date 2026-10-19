package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.LotStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G7c: move {@code quantity} of {@code parentLotId} in one warehouse into a new child lot with its own
 * status. The child inherits the parent's expiry and records what created it ({@code sourceReference*}).
 * Total on-hand of the item in the warehouse does not change.
 */
public record LotSplitCommand(
        UUID itemId,
        UUID warehouseId,
        UUID parentLotId,
        String childLotCode,
        LotStatus childStatus,
        BigDecimal quantity,
        String sourceReferenceType,
        UUID sourceReferenceId,
        String reason
) {}
