package com.erp.manufacturing.module.workorder.domain;

/** G8: the work order commands that accept an {@code Idempotency-Key}; part of the key's scope. */
public enum WorkOrderCommand {
    WORK_ORDER_UPDATE,
    WORK_ORDER_PLAN,
    WORK_ORDER_RELEASE,
    WORK_ORDER_CANCEL,
    RESERVATION_CREATE,
    RESERVATION_AUTO,
    RESERVATION_RELEASE,
    RESERVATION_RELEASE_ALL,
    ISSUE_APPROVE,
    ISSUE_REJECT,
    ISSUE_CANCEL
}
