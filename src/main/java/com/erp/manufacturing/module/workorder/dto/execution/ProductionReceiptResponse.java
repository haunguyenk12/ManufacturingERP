package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Flat single-line shape (spec §6.3/§6.4). The {@code lines[]} array was collapsed in F5 —
 * see {@link ProductionReceiptPostRequest} for why.
 */
public record ProductionReceiptResponse(
        UUID receiptId,
        /** Human-facing document number (spec §6.4). Null for receipts created before F5. */
        String code,
        UUID workOrderId,
        /** Work order document number (spec §6.4 "Receipt"). */
        String workOrderCode,
        String status,
        String idempotencyKey,
        /** NON_TRACKED / LOT_TRACKED — QC disposition only applies to LOT_TRACKED output. */
        String outputTrackingMethod,
        UUID itemId,
        String itemSku,
        /** Output item name (spec §6.4 "Receipt": {@code outputItemSku, outputItemName, ...}). */
        String itemName,
        /** Unit of measure of the output item (spec §6.4). */
        String uom,
        UUID destinationWarehouseId,
        String destinationWarehouseCode,
        UUID lotId,
        String lotNumber,
        /**
         * HOLD / AVAILABLE / REJECTED (spec §6.4 "Output lot"). Null when the output is not
         * lot-tracked — for those the QC verdict lives in {@link #qcResult()} instead (D5, B40).
         */
        String outputLotStatus,
        BigDecimal quantity,
        UUID stockMovementId,
        Instant postedAt,
        String note,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt,
        String rejectReason,
        String qcResult,
        String qcReason,
        Instant qcAt,
        String createdByUsername,
        String approvedByUsername,
        String qcByUsername,
        /** Business trace of this document. Not the {@code X-Trace-Id} debug header. */
        String traceId,
        /** Traces of the shop-floor reports this receipt drew from, oldest first. */
        List<String> sourceWipTraceIds
) {}
