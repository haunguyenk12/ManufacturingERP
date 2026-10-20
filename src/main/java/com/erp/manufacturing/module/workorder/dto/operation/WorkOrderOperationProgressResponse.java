package com.erp.manufacturing.module.workorder.dto.operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * G8c (FE R10–R12, R14): one operation of a work order and its pools. Everything but {@code completedAt} is derived
 * from the input ledger and the executions recorded against the operation — no counter column can drift from them.
 *
 * <p>When {@code inputKnown} is {@code false} (a work order that had production reported before V81) the input
 * and the pools that depend on it are {@code null}, not 0: they were never recorded and are not guessed (FE Q22).
 */
public record WorkOrderOperationProgressResponse(
        UUID workOrderOperationId,
        Integer sequence,
        String name,
        String workCenterCode,
        /** {@code PENDING} · {@code READY} · {@code IN_PROGRESS} · {@code COMPLETED}. */
        String status,
        boolean inputKnown,
        BigDecimal inputQuantity,
        BigDecimal freshProcessedQuantity,
        /** {@code input − fresh processed}: what fresh executions may still use. */
        BigDecimal freshRemainingQuantity,
        BigDecimal goodQuantity,
        BigDecimal scrapQuantity,
        /** Every rework ever recorded on the operation, retries included. */
        BigDecimal historicalReworkQuantity,
        /** {@code historical rework − processed by retries}: what retries may still use. */
        BigDecimal pendingReworkQuantity,
        /** Good already handed to the next operation; {@code null} on the last operation. */
        BigDecimal transferredQuantity,
        /** {@code good − transferred}; {@code null} on the last operation (its good is the work order's final good). */
        BigDecimal transferableQuantity,
        long executionCount,
        Instant completedAt,
        UUID completedBy,
        /** The work order version after this read or command — the next command's expected version. */
        Long workOrderVersion
) {}
