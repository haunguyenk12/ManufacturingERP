package com.erp.manufacturing.module.inventory.service;

import java.util.UUID;

/** G10b: one stock position to lock — {@code lotId} {@code null} for an item that is not lot-tracked. */
public record StockPositionRef(UUID itemId, UUID warehouseId, UUID lotId) {}
