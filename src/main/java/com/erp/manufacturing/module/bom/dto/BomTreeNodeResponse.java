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
        List<BomTreeNodeResponse> components,
        /** Output quantity of this node's own BOM; {@code null} when the node has no BOM (G5, Q6). */
        BigDecimal outputQuantity,
        /** {@code quantityPer × outputQuantity} of the PARENT BOM; {@code null} on the root (G5, Q6). */
        BigDecimal componentQuantity
) {}
