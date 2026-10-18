package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /v1/purchase-orders/{id}/confirm} (G7a). */
public record PurchaseOrderVersionRequest(@NotNull Long expectedVersion) {}
