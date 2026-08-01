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
        BigDecimal shortageQuantity
) {}
