package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /work-orders/{id}/release-reservations} (G8a). Lists what went back to free stock — the
 * unissued part of each {@code ACTIVE} reservation — and the work order version to send next. No stock
 * movement is written: on hand never changed, only reserved did. Read readiness afterwards from
 * {@code GET /material-readiness}.
 */
public record WorkOrderReservationReleaseResponse(
        UUID workOrderId,
        Long workOrderVersion,
        int releasedReservationCount,
        List<Line> lines
) {

    public record Line(UUID reservationId, UUID componentLineId, UUID itemId, UUID warehouseId, UUID lotId,
                       BigDecimal releasedQuantity) {}
}
