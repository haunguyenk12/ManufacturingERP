package com.erp.manufacturing.module.inventory.domain;

public enum MovementType {
    RECEIVE,
    ISSUE,
    ADJUST_IN,
    ADJUST_OUT,
    REVERSAL,
    /** QC disposition of a lot. Traceability only — on-hand, reserved and balances are unchanged. */
    LOT_STATUS_CHANGE,
    /** G4-2: source side of a stock transfer line. Always paired with a {@link #TRANSFER_IN}. */
    TRANSFER_OUT,
    /** G4-2: destination side of a stock transfer line. */
    TRANSFER_IN
}
