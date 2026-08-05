package com.erp.manufacturing.module.costing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code totalStandardCost} is computed at read time via {@code CostingService.calculateStandardCost}
 * (not stored) so it always reflects the item's current BOM, not a stale snapshot.
 */
public record ItemStandardCostResponse(
        UUID itemStandardCostId,
        UUID companyId,
        UUID itemId,
        String itemCode,
        BigDecimal materialCost,
        BigDecimal laborCost,
        BigDecimal overheadCost,
        BigDecimal totalStandardCost,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
