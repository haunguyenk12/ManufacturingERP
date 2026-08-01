package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the "which work order am I warehousing output from?" screen (spec §6.4 "Candidate").
 *
 * <p>A lean read model rather than {@code WorkOrderResponse}, for the same reason
 * {@link ProductionExecutionCandidateResponse} is: that record carries 40-odd fields and resolves
 * demand allocations, none of which this screen shows.
 *
 * <p>Do not confuse it with {@link ProductionExecutionCandidateResponse}, which looks similar and
 * answers a different question. That one lists work orders that may still <em>report</em>
 * production and therefore excludes {@code COMPLETED}; this one lists work orders that still have
 * output to <em>warehouse</em> and therefore includes it (invariants B75 vs B76).
 *
 * @param availableToReceipt what may actually be receipted right now — already net of receipts still
 *        in {@code DRAFT}/{@code PENDING_APPROVAL} (B16). It is deliberately <em>not</em>
 *        {@code actualGoodQuantity - receiptedQuantity}: showing that instead would offer a number
 *        the create endpoint then refuses.
 */
public record ProductionReceiptCandidateResponse(
        UUID workOrderId,
        String workOrderCode,
        String status,
        UUID outputItemId,
        String outputItemSku,
        String outputItemName,
        String uom,
        BigDecimal actualGoodQuantity,
        /** How much has already been warehoused — the work order's {@code completedQuantity}. */
        BigDecimal receiptedQuantity,
        BigDecimal availableToReceipt,
        /** NON_TRACKED / LOT_TRACKED — decides whether the create form must ask for a lot number. */
        String outputTrackingMethod,
        Instant plannedEndAt
) {}
