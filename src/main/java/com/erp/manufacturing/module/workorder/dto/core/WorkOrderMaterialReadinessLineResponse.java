package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkOrderMaterialReadinessLineResponse(
        UUID componentLineId,
        UUID itemId,
        String itemSku,
        String itemName,
        BigDecimal requiredQuantity,
        BigDecimal issuedQuantity,
        /**
         * The same aggregate as {@link WorkOrderComponentLineResponse#reservedQuantity()} — both come
         * from {@code MaterialReservationRepository.sumActiveRemaining*}. Two screens showing
         * different numbers under one name would be worse than the field being absent, so if this
         * definition changes, change both.
         */
        BigDecimal reservedQuantity,
        /** CF-2: shortage counts only the eligible part of {@code reservedQuantity}. */
        BigDecimal shortageQuantity,
        /**
         * CF-2 (B265): the part of {@code reservedQuantity} on a lot that is no longer issuable (not AVAILABLE or past
         * its expiry). It does not cover the requirement; the reservation itself is left in place.
         */
        BigDecimal ineligibleReservedQuantity,
        /** CF-2: {@code RESERVED_LOT_INELIGIBLE} when {@code ineligibleReservedQuantity > 0}, otherwise {@code null}. */
        String blocker
) {
    public static final String BLOCKER_RESERVED_LOT_INELIGIBLE = "RESERVED_LOT_INELIGIBLE";

    /** Pre-CF-2 shape: no ineligible reservation. */
    public WorkOrderMaterialReadinessLineResponse(UUID componentLineId, UUID itemId, String itemSku, String itemName,
                                                  BigDecimal requiredQuantity, BigDecimal issuedQuantity,
                                                  BigDecimal reservedQuantity, BigDecimal shortageQuantity) {
        this(componentLineId, itemId, itemSku, itemName, requiredQuantity, issuedQuantity, reservedQuantity,
                shortageQuantity, BigDecimal.ZERO, null);
    }

    /** Reserved quantity that actually covers the requirement. */
    public BigDecimal eligibleReservedQuantity() {
        return reservedQuantity.subtract(ineligibleReservedQuantity == null ? BigDecimal.ZERO : ineligibleReservedQuantity);
    }
}
