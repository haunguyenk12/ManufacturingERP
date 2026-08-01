package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.util.UUID;

public record MaterialIssueLineResponse(
        UUID issueLineId,
        UUID componentLineId,
        UUID reservationId,
        UUID itemId,
        String itemSku,
        /** Component name (spec §4.2 "History": {@code itemSku, itemName, quantity, uom}). */
        String itemName,
        /** Unit of measure of the component item (spec §4.2). */
        String uom,
        UUID warehouseId,
        String warehouseCode,
        UUID lotId,
        String lotNumber,
        BigDecimal quantity,
        UUID stockMovementId,
        boolean overIssue,
        String overrideReason
) {}
