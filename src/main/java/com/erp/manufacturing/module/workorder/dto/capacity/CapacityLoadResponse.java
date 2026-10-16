package com.erp.manufacturing.module.workorder.dto.capacity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One work center's load over a period (G5, FE §3.6). Every figure is in capacity minutes:
 * {@code availableMinutes} already multiplies working minutes by {@code capacityUnits}.
 *
 * <p>{@code demandMinutes} = base + the full required minutes of a preview; {@code allocatedMinutes} =
 * base + only the preview's in-period part. Without a preview both equal the base. Available,
 * overload, utilisation and status are {@code null} when the work center has no calendar — unknown,
 * not zero.
 */
public record CapacityLoadResponse(
        UUID workCenterId,
        String workCenterCode,
        String workCenterName,
        Integer capacityUnits,
        BigDecimal baseInPeriodMinutes,
        BigDecimal previewRequiredMinutes,
        BigDecimal demandMinutes,
        BigDecimal allocatedMinutes,
        BigDecimal availableMinutes,
        BigDecimal overloadMinutes,
        BigDecimal utilizationPercent,
        /** {@code AVAILABLE} / {@code NEAR_CAPACITY} (≥ 85 %) / {@code OVERLOADED}; {@code null} without a calendar. */
        String status
) {}
