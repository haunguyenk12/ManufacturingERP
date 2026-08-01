package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One shop-floor posting plus the work order totals it produced (spec §5.2).
 *
 * <p>The {@code workOrder*} block is the "Summary" panel of that screen. Note that
 * {@link #workOrderRemainingGoodQuantity()} is {@code planned - actualGood} — what the shop floor
 * still has to <em>make</em>. It is deliberately <b>not</b> the same number as
 * {@code WorkOrderResponse.remainingQuantity}, which is {@code planned - completed} — what still has
 * to be <em>warehoused</em>. Both are legitimate; putting them in one record is what would make them
 * indistinguishable, which is why they live apart.
 *
 * @see com.erp.manufacturing.module.workorder.dto.core.WorkOrderResponse
 */
public record ProductionExecutionResponse(
        UUID productionExecutionId,
        /** Document number (spec §5.2 "History"), e.g. {@code PE-3F2A9C01}. */
        String code,
        /**
         * Always {@code "POSTED"} (spec §5.2 "History: status"). Deliberately <b>not</b> a column:
         * a production execution has no cancel or reversal path, so a stored value that can never
         * be anything else is speculative (coding-rules.md §11.5) — the same reasoning behind the
         * {@code bomCapturedAt}/{@code executionCompletedAt} aliases of F8. If a reversal is ever
         * added, this is the field that becomes a real column and an enum.
         */
        String status,
        UUID workOrderId,
        /** Work order document number (spec §5.2 "WO selector"). */
        String workOrderCode,
        UUID workOrderOperationId,
        Integer operationSequence,
        String operationName,
        String workCenterCode,
        BigDecimal goodQuantity,
        BigDecimal scrapQuantity,
        BigDecimal reworkQuantity,
        Instant actualStartedAt,
        Instant actualEndedAt,
        UUID operatorUserId,
        /** Resolved in one batch query per page (rule C14); null when the user no longer exists. */
        String operatorUsername,
        String traceId,
        String notes,
        /** Unit of measure of the work order's output item (spec §5.2 "Context"). */
        String uom,
        /** Work order totals after this report — saves the shop-floor UI a second round trip. */
        BigDecimal workOrderActualGoodQuantity,
        BigDecimal workOrderActualScrapQuantity,
        BigDecimal workOrderActualReworkQuantity,
        BigDecimal workOrderAvailableToReceipt,
        /** {@code planned - actualGood}: what the shop floor still has to make (spec §5.2). */
        BigDecimal workOrderRemainingGoodQuantity,
        /** {@code actualGood / planned} as a percentage, scale 2, HALF_UP (spec §5.2). */
        BigDecimal workOrderCompletionPercent,
        String workOrderStatus,
        Instant createdAt
) {}
