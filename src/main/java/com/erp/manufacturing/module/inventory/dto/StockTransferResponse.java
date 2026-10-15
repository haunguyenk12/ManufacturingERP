package com.erp.manufacturing.module.inventory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transfer document (G4-2). {@code status} is always {@code POSTED} — there is no other state in G4.
 * {@code lines} is present on create/detail and {@code null} on list rows (rule C13: no collection
 * fetch next to pagination).
 */
public record StockTransferResponse(
        UUID transferId,
        String code,
        String status,
        UUID plantId,
        UUID fromWarehouseId,
        String fromWarehouseCode,
        String fromWarehouseName,
        UUID toWarehouseId,
        String toWarehouseCode,
        String toWarehouseName,
        String reason,
        Instant postedAt,
        String traceId,
        UUID createdBy,
        String createdByUsername,
        Long version,
        List<StockTransferLineResponse> lines
) {}
