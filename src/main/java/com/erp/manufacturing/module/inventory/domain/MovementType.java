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
    TRANSFER_IN,
    /** G7c: quantity leaving a lot that a purchase QC decision splits into a child lot. Always paired with {@link #LOT_SPLIT_IN}. */
    LOT_SPLIT_OUT,
    /** G7c: the same quantity arriving in the child lot, same warehouse. On-hand is unchanged. */
    LOT_SPLIT_IN
}
