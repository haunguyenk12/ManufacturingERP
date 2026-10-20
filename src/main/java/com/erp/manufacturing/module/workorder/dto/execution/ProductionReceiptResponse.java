package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
        /** NON_TRACKED / LOT_TRACKED / SERIAL_TRACKED — QC disposition applies to both tracked kinds. */
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
        UUID serialId,
        String serialNumber,
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
        List<String> sourceWipTraceIds,
        // ---- G9a (FE §3.2) ----
        Long version,
        /** The work order's version: every receipt command moves it on (FE Q12). */
        Long workOrderVersion,
        UUID plantId,
        String plantTimeZone,
        LocalDate receivedOn,
        LocalDate expiresOn,
        String destinationWarehouseName,
        String submittedByUsername,
        String rejectedByUsername,
        Instant withdrawnAt,
        String withdrawReason,
        String withdrawnByUsername,
        Instant cancelledAt,
        String cancelReason,
        String cancelledByUsername,
        /** G — final good of the work order: good of its last operation, retries included (B219). */
        BigDecimal actualFinalGoodQuantity,
        /** C = min(planned, G): what may ever be receipted (D12). */
        BigDecimal receiptableGoodCap,
        /** A — what approved receipts already put in stock (the work order's {@code completedQuantity}). */
        BigDecimal approvedReceiptQuantity,
        /** D — what DRAFT and PENDING_APPROVAL receipts of the work order claim. */
        BigDecimal claimedQuantity,
        /** W = max(0, C − A): produced output not in stock yet. */
        BigDecimal awaitingReceiptQuantity,
        /** U = max(0, C − A − D): what a new receipt may still claim. */
        BigDecimal unclaimedReceiptQuantity,
        /** What the caller may do on this receipt right now (EDIT, SUBMIT, WITHDRAW, CANCEL, APPROVE, REJECT, QC). */
        List<String> allowedActions
) {}
