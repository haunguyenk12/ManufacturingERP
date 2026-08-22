package com.erp.manufacturing.module.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A recent ledger row as the dashboard renders it: business labels resolved server-side so the card
 * needs no follow-up call to Item, Warehouse or User.
 *
 * <p>Deliberately separate from {@link StockMovementResponse}, which stays the lean technical shape
 * of {@code GET /inventory/movements} — the two endpoints answer different questions and the label
 * joins are only worth paying for on the dashboard's fixed-size page.
 */
public record DashboardRecentMovementResponse(
        UUID movementId,
        @Schema(description = "RECEIVE | ISSUE | ADJUST_IN | ADJUST_OUT | REVERSAL | LOT_STATUS_CHANGE")
        String movementType,
        @Schema(description = "IN | OUT | NONE. NONE is used by LOT_STATUS_CHANGE, which moves no stock.")
        String direction,
        UUID itemId,
        String itemCode,
        String itemName,
        @Schema(description = "Display unit of the item (items.unit)", example = "PCS")
        String uomCode,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        @Schema(description = "Null for items that are not lot-tracked")
        UUID lotId,
        @Schema(description = "Null for items that are not lot-tracked")
        String lotCode,
        BigDecimal quantity,
        String reason,
        @Schema(description = "Free-text classifier written by the posting service "
                + "(WORK_ORDER, GOODS_RECEIPT, …); null for a movement posted directly through the "
                + "inventory API without one")
        String referenceType,
        @Schema(description = "Id of the source document, paired with referenceType. Not a business "
                + "document number.")
        String referenceId,
        @Schema(description = "Who posted the movement (stock_movements.created_by); null for rows "
                + "written outside a user request")
        UUID actorUserId,
        @Schema(description = "Username of actorUserId, resolved in one batch query; null when the "
                + "actor is unknown or the user record no longer exists")
        String actorUsername,
        Instant createdAt
) {}
