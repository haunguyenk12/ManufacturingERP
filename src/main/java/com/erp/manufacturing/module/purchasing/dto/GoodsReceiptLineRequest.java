package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record GoodsReceiptLineRequest(
        @NotNull UUID purchaseOrderLineId,
        UUID lotId,
        String lotCode,
        @NotNull @Positive BigDecimal receivedQuantity
) {}
