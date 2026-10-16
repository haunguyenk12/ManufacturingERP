package com.erp.manufacturing.module.bom.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BomLineResponse(
        UUID lineId,
        UUID bomId,
        Integer lineNo,
        UUID componentItemId,
        String componentItemCode,
        String componentItemName,
        BigDecimal quantityPer,
        BigDecimal scrapRate,
        /** {@code quantityPer × outputQuantity} of the BOM — the batch figure the form was entered in (G5, D1). */
        BigDecimal componentQuantity,
        String componentUomCode
) {}
