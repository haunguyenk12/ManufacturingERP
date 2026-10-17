package com.erp.manufacturing.module.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A stock position a line could be held from (G6a): free quantity above zero, lot AVAILABLE and not
 * expired. {@code version} is the position version, for display only — the hold itself is guarded by
 * the sales order version.
 */
public record SalesOrderAllocationCandidateResponse(
        UUID balanceId,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        UUID lotId,
        String lotCode,
        Instant lotExpiresAt,
        BigDecimal onHandQuantity,
        BigDecimal availableQuantity,
        Long version
) {}
