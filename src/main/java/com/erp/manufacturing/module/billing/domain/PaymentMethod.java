package com.erp.manufacturing.module.billing.domain;

/** G10d (FE §3.3): how the money came in. A record only — no bank or gateway behind it. */
public enum PaymentMethod {
    CASH,
    TRANSFER,
    OTHER
}
