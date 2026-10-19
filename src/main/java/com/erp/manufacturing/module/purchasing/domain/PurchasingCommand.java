package com.erp.manufacturing.module.purchasing.domain;

/** G7a: the purchasing commands that accept an {@code Idempotency-Key}; part of the key's scope. */
public enum PurchasingCommand {
    PURCHASE_ORDER_CREATE_DRAFT,
    PURCHASE_ORDER_UPDATE,
    PURCHASE_ORDER_CONFIRM,
    PURCHASE_ORDER_CANCEL,
    PURCHASE_ORDER_CLOSE,
    GOODS_RECEIPT_CREATE_DRAFT,
    GOODS_RECEIPT_UPDATE,
    GOODS_RECEIPT_POST,
    GOODS_RECEIPT_CANCEL,
    PURCHASE_QC_DECISION
}
