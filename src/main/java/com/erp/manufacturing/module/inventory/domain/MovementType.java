package com.erp.manufacturing.module.inventory.domain;

public enum MovementType {
    RECEIVE,
    ISSUE,
    ADJUST_IN,
    ADJUST_OUT,
    REVERSAL,
    /** QC disposition of a lot. Traceability only — on-hand, reserved and balances are unchanged. */
    LOT_STATUS_CHANGE
}
