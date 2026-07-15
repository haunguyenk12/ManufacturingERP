package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.StockMovement;

public record InventoryMovementResult(
        StockMovement movement,
        boolean created
) {}
