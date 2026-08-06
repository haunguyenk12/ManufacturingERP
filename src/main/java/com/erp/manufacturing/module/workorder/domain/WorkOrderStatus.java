package com.erp.manufacturing.module.workorder.domain;

public enum WorkOrderStatus {
    DRAFT,
    /**
     * Scheduled but not yet being prepared (spec §3.1). Sits between {@code DRAFT} and the
     * reservation/release cycle: a planner has committed to the dates, the shop floor has not
     * started reserving material. Behaves exactly like {@code DRAFT} for execution — no
     * reservation, issue, receipt, or WIP is allowed (invariant B13).
     */
    PLANNED,
    BLOCKED,
    RELEASED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    /**
     * Terminal reconciliation state (P6). Reachable only from {@code COMPLETED}, via an explicit
     * manager action — never automatic. Locks the work order completely: no further reservation,
     * issue, receipt, or adjustment is possible (invariant B100).
     */
    CLOSED
}
