package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /v1/goods-receipts/{id}/post} (G7b). */
public record GoodsReceiptVersionRequest(@NotNull Long expectedVersion) {}
