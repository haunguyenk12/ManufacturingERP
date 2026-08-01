package com.erp.manufacturing.module.workorder.domain;

/**
 * Lifecycle of a production receipt document (F2, spec §6.1):
 * {@code DRAFT → PENDING_APPROVAL → APPROVED} with {@code REJECTED} as the terminal branch.
 * Stock is only touched on the transition into {@code APPROVED}.
 */
public enum ProductionReceiptStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED
}
