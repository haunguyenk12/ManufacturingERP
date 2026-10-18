package com.erp.manufacturing.module.dataimport.dto;

import com.erp.manufacturing.module.dataimport.domain.DataImportKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A fixed-template import batch (G3, FE_HANDOFF_GD_03 §3.4).
 *
 * <p>{@code version} is the optimistic-lock counter: {@code 0} right after upload, bumped by every
 * state change. Send back exactly what was received as {@code expectedVersion}.
 *
 * <p>{@code rowCount = validRowCount + errorRowCount}; {@code errorRowCount} counts rows, not issues.
 * When {@code COMMITTED}: {@code createdCount = rowCount = createdIds.size()}, in row order.
 */
public record DataImportBatchResponse(
        UUID id,
        UUID actorId,
        UUID companyId,
        UUID plantId,
        DataImportKind kind,
        String locale,
        String templateVersion,
        String fileName,
        long fileSize,
        String status,
        long version,
        Instant createdAt,
        Instant committedAt,
        int rowCount,
        int validRowCount,
        int errorRowCount,
        int createdCount,
        List<DataImportBatchRowResponse> rows,
        List<UUID> createdIds,
        /**
         * G5, BOM batches only: number of BOM groups (distinct BOM codes) in the file — what a commit
         * creates. {@code createdIds} then holds one id per BOM, not one per row. {@code null} for
         * every other kind.
         */
        Integer bomCount,
        /**
         * G6c, sales order batches only: number of order groups (distinct group codes) in the file — the
         * number of DRAFT orders a commit creates. {@code createdIds} then holds one id per order.
         * {@code null} for every other kind.
         */
        Integer salesOrderCount
) {
}
