package com.erp.manufacturing.module.dataimport.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A run and its counters.
 *
 * <p>{@code detectedHeaders} and {@code unmappedHeaders} are both present on purpose. The first is
 * what the file had; the second is the part the chosen profile ignores. Showing only the first would
 * leave the user to diff two lists by eye, which is precisely the moment a mismatched column slips
 * through unnoticed.
 *
 * <p>{@code unmappedHeaders} is empty until a profile has been chosen — before that, nothing is
 * mapped and calling every column "ignored" would be alarming and useless.
 */
public record ImportRunResponse(UUID importRunId,
                                String code,
                                String targetType,
                                UUID profileId,
                                String profileCode,
                                UUID companyId,
                                UUID plantId,
                                UUID warehouseId,
                                String originalFilename,
                                Long fileSizeBytes,
                                String fileSha256,
                                List<String> detectedHeaders,
                                List<String> unmappedHeaders,
                                String status,
                                Integer totalRows,
                                Integer validRows,
                                Integer errorRows,
                                Integer appliedRows,
                                Integer failedRows,
                                Instant parsedAt,
                                Instant validatedAt,
                                Instant appliedAt,
                                String errorMessage,
                                Instant createdAt) {
}
