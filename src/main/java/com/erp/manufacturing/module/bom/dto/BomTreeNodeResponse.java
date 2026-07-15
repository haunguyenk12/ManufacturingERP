package com.erp.manufacturing.module.bom.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BomTreeNodeResponse(
        UUID itemId,
        String itemCode,
        String itemName,
        UUID bomId,
        String revision,
        boolean hasActiveBom,
        BigDecimal quantityPer,
        BigDecimal scrapRate,
        List<BomTreeNodeResponse> components
) {}
