package com.erp.manufacturing.module.workorder.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One reservation as seen from the warehouse (G4-4, "Giữ cho sản xuất").
 * {@code remainingQuantity = quantity − consumedQuantity} — the part still held back from free stock
 * while the reservation is {@code ACTIVE}.
 *
 * <p>Since G6a a reservation may belong to a sales order line instead of a work order:
 * {@code ownerType} is {@code WORK_ORDER} or {@code SALES_ORDER}, and the fields of the other owner
 * are {@code null}. A sales-order row never carries a fake work order.
 */
public record InventoryReservationResponse(
        UUID reservationId,
        UUID workOrderId,
        String workOrderNo,
        String workOrderStatus,
        UUID componentLineId,
        Integer componentLineNo,
        UUID itemId,
        String itemCode,
        String itemName,
        String uom,
        UUID warehouseId,
        UUID lotId,
        String lotCode,
        BigDecimal quantity,
        BigDecimal consumedQuantity,
        BigDecimal remainingQuantity,
        String status,
        Instant createdAt,
        String ownerType,
        UUID salesOrderId,
        String salesOrderNo,
        UUID salesOrderLineId,
        Integer salesOrderLineNo
) {}
