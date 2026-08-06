package com.erp.manufacturing.module.inventory.domain;

/**
 * How an item's stock is traced. The frontend contract (spec §6.4) expects this as a named value
 * instead of the {@code boolean lotTracked} flag stored on {@link Item}; it is derived, not persisted.
 */
public enum TrackingMethod {

    NON_TRACKED,
    LOT_TRACKED,
    SERIAL_TRACKED;

    public static TrackingMethod of(boolean lotTracked, boolean serialTracked) {
        if (lotTracked) {
            return LOT_TRACKED;
        }
        return serialTracked ? SERIAL_TRACKED : NON_TRACKED;
    }
}
