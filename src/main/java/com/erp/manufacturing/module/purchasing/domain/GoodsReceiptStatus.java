package com.erp.manufacturing.module.purchasing.domain;

/**
 * G7b (D3): {@code DRAFT} is saved and edited without touching stock; posting moves it to
 * {@code POSTED}. Cancelling a DRAFT never touches stock; cancelling a POSTED receipt keeps the
 * legacy REVERSAL path.
 */
public enum GoodsReceiptStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
