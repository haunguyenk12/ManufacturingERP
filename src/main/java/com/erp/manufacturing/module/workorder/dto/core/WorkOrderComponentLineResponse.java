package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkOrderComponentLineResponse(
        UUID componentLineId,
        UUID bomLineId,
        Integer lineNo,
        UUID componentItemId,
        String componentItemCode,
        String componentItemName,
        /** Unit of measure of the component item (spec §3.3 "Component"). */
        String uom,
        BigDecimal quantityPer,
        BigDecimal scrapRate,
        BigDecimal requiredQuantity,
        /**
         * Material still held for this component by {@code ACTIVE} reservations, i.e.
         * {@code sum(quantity - consumedQuantity)} — reserved but not yet issued (spec §3.3
         * "Requirement").
         *
         * <p>Deliberately the <em>same</em> aggregate
         * {@link WorkOrderMaterialReadinessLineResponse#reservedQuantity()} reports, so the work
         * order detail and the readiness screen can never disagree about a number they both call
         * {@code reservedQuantity}. If this ever needs to change, change both.
         *
         * <p>Zero — never null — when nothing is reserved: the aggregate query returns no row at all
         * for such a line, and the caller defaults it.
         */
        BigDecimal reservedQuantity,
        BigDecimal issuedQuantity,
        BigDecimal remainingQuantity
) {}
