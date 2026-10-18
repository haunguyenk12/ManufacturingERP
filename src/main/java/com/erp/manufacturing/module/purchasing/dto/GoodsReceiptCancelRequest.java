package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body of {@code POST /v1/goods-receipts/{id}/cancel}. G7b adds {@code expectedVersion}
 * (optional — {@code null} keeps the legacy behaviour without a version check).
 */
public record GoodsReceiptCancelRequest(
        @Size(max = 2000) String cancelNote,
        Long expectedVersion
) {

    /** Pre-G7b arity. */
    public GoodsReceiptCancelRequest(String cancelNote) {
        this(cancelNote, null);
    }
}
