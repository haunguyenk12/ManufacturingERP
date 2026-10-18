package com.erp.manufacturing.module.purchasing.domain;

/**
 * G7a (D1): {@code SENT} became {@code CONFIRMED} and {@code RECEIVED} became {@code FULLY_RECEIVED}
 * (V76 renamed the stored values); {@code CLOSED} is new — the remainder of a confirmed order was
 * given up on, what was received stays.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_RECEIVED,
    FULLY_RECEIVED,
    CLOSED,
    CANCELLED
}
