package com.erp.manufacturing.module.purchasing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GoodsReceiptLineResponse(
        UUID goodsReceiptLineId,
        UUID purchaseOrderLineId,
        UUID itemId,
        String itemCode,
        String itemName,
        UUID lotId,
        String lotCode,
        BigDecimal receivedQuantity,
        UUID stockMovementId
) {}
