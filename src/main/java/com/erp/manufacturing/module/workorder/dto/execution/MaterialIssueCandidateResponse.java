package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One place a component can be issued from (G8b, {@code GET /work-orders/{id}/material-issue-candidates}).
 * {@code NORMAL} lists the work order's own active reservations; {@code SUPPLEMENTAL} lists free stock of the
 * component in every warehouse of the plant (FE Q8). The figures are a snapshot ({@code asOf}) — posting and
 * approval check the stock again.
 *
 * @param sourceType        {@code RESERVATION} or {@code FREE_STOCK}
 * @param availableQuantity reservation: its unissued part; free stock: on hand − reserved − quality hold
 * @param eligible          {@code false} when the source cannot be issued now — {@code ineligibleReason} says why
 * @param suggestedQuantity normal mode only: {@code min(available, required − gross issued)}, never below 0;
 *                          {@code null} in supplemental mode, which is by definition past the plan
 */
public record MaterialIssueCandidateResponse(
        UUID componentLineId,
        Integer lineNo,
        UUID itemId,
        String itemCode,
        String itemName,
        String uom,
        BigDecimal requiredQuantity,
        BigDecimal grossIssuedQuantity,
        String sourceType,
        UUID reservationId,
        UUID warehouseId,
        String warehouseCode,
        UUID lotId,
        String lotCode,
        Instant lotExpiresAt,
        BigDecimal availableQuantity,
        boolean eligible,
        String ineligibleReason,
        BigDecimal suggestedQuantity,
        Instant asOf
) {}
