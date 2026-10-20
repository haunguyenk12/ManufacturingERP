package com.erp.manufacturing.module.workorder.dto.wip;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One posted issue line of a work order and where its material went (G8d, FE R15): issued, used by executions,
 * returned, and what is left — {@code unused = issued − consumed − returned}. When the work order's consumption is not
 * known (reported before G8c, FE Q22) {@code consumed}, {@code unused} and {@code returnable} are null, never a guessed
 * 0. {@code returnable} is the unused part when its source can take it back now, 0 when it cannot
 * ({@code ineligibleReason} says why). The same row serves the WIP screen and the return candidates.
 */
public record WipMaterialLineResponse(
        UUID issueLineId,
        UUID issueId,
        String issueCode,
        Instant postedAt,
        UUID componentLineId,
        UUID itemId,
        String itemSku,
        String itemName,
        String uom,
        UUID warehouseId,
        String warehouseCode,
        UUID lotId,
        String lotNumber,
        BigDecimal issuedQuantity,
        boolean consumptionKnown,
        BigDecimal consumedQuantity,
        BigDecimal returnedQuantity,
        BigDecimal unusedQuantity,
        BigDecimal returnableQuantity,
        boolean eligible,
        String ineligibleReason
) {}
