package com.erp.manufacturing.module.dataimport.dto;

import com.erp.manufacturing.module.dataimport.domain.DataImportKind;

import java.time.Instant;
import java.util.UUID;

/** History row of a fixed-template batch: everything of {@link DataImportBatchResponse} except rows/ids. */
public record DataImportBatchSummaryResponse(
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
        int createdCount
) {
}
