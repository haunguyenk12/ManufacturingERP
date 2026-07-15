package com.erp.manufacturing.module.inventory.service;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryIssueCommand(
        UUID itemId,
        UUID warehouseId,
        UUID lotId,
        String lotCode,
        BigDecimal quantity,
        String reason,
        String referenceType,
        String referenceId
) {}
