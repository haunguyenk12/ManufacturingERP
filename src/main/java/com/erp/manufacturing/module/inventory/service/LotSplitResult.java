package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.StockMovement;

/** G7c: the child lot a {@link LotSplitCommand} created and its two ledger rows. */
public record LotSplitResult(
        InventoryLot childLot,
        StockMovement outMovement,
        StockMovement inMovement
) {}
