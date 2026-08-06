package com.erp.manufacturing.module.inventory.domain;

/**
 * A serial always represents exactly one physical unit (unlike a lot, which is a fungible bucket).
 * There is no {@code HOLD} state: serial-tracked production output is available immediately on
 * approval, mirroring how non-lot-tracked output already behaves (D5) — see
 * {@code module/workorder/CLAUDE.md} for the invariant this trades off.
 */
public enum SerialStatus {

    AVAILABLE,
    REJECTED,
    ISSUED
}
