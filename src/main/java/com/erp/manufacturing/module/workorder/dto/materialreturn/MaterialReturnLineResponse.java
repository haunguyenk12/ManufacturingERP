package com.erp.manufacturing.module.workorder.dto.materialreturn;

import java.math.BigDecimal;
import java.util.UUID;

/** One line of a material return, with the source issue line it came from and the stock movement it posted. */
public record MaterialReturnLineResponse(
        UUID materialReturnLineId,
        int lineNo,
        UUID sourceIssueLineId,
        UUID sourceIssueId,
        String sourceIssueCode,
        UUID componentLineId,
        UUID itemId,
        String itemSku,
        String uom,
        UUID warehouseId,
        String warehouseCode,
        UUID lotId,
        String lotNumber,
        BigDecimal quantity,
        UUID stockMovementId
) {}
