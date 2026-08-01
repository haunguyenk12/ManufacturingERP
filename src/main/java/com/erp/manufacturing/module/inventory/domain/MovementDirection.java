package com.erp.manufacturing.module.inventory.domain;

public enum MovementDirection {
    IN,
    OUT,
    /**
     * Movement that records an event without moving quantity in or out
     * ({@link MovementType#LOT_STATUS_CHANGE}). Summing the ledger by direction stays correct
     * because these rows belong to neither side.
     */
    NONE
}
