package com.erp.manufacturing.module.sales.domain;

/** G10: the commands an {@code Idempotency-Key} is scoped to in {@code commercial_idempotency_records}. */
public enum CommercialCommand {
    SALES_PRICE_SET,
    DELIVERY_CREATE,
    DELIVERY_UPDATE,
    DELIVERY_POST,
    DELIVERY_CANCEL,
    INVOICE_CREATE,
    INVOICE_UPDATE,
    INVOICE_POST,
    INVOICE_CANCEL,
    PAYMENT_CREATE,
    PAYMENT_UPDATE,
    PAYMENT_POST,
    PAYMENT_CANCEL
}
