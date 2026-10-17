package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.sales.service.SalesOrderAllocationTarget;
import com.erp.manufacturing.module.sales.service.SalesOrderFulfillmentService;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.dto.InventoryReservationResponse;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only list of reservations held in one warehouse (G4-4; sales-order reservations since G6a).
 * Lives in {@code workorder} because the reservation aggregate does; guarded with the inventory read
 * permission because the question it answers is "why is this stock not free".
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationQueryService {

    private final MaterialReservationRepository reservationRepository;
    private final SalesOrderFulfillmentService salesOrderFulfillmentService;

    /** Sales-order labels for the page come from one batch lookup (rule C14), never one call per row. */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId)")
    public PageResult<InventoryReservationResponse> list(UUID warehouseId, UUID itemId, UUID lotId,
                                                         MaterialReservationStatus status, Pageable pageable) {
        Page<MaterialReservation> page = reservationRepository
                .searchByWarehouse(warehouseId, itemId, lotId, status, pageable);
        Map<UUID, SalesOrderAllocationTarget> salesLines = salesOrderFulfillmentService.findAllocationTargets(
                page.getContent().stream()
                        .map(MaterialReservation::getSalesOrderLineId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        return PageResult.from(page.map(reservation -> toResponse(reservation, salesLines)));
    }

    private InventoryReservationResponse toResponse(MaterialReservation reservation,
                                                    Map<UUID, SalesOrderAllocationTarget> salesLines) {
        Item item = reservation.getItem();
        InventoryLot lot = reservation.getLot();
        boolean forSalesOrder = reservation.isForSalesOrder();
        SalesOrderAllocationTarget salesLine = forSalesOrder ? salesLines.get(reservation.getSalesOrderLineId()) : null;
        return new InventoryReservationResponse(
                reservation.getReservationId(),
                forSalesOrder ? null : reservation.getWorkOrder().getWorkOrderId(),
                forSalesOrder ? null : reservation.getWorkOrder().getWorkOrderNo(),
                forSalesOrder ? null : reservation.getWorkOrder().getStatus().name(),
                forSalesOrder ? null : reservation.getComponentLine().getComponentLineId(),
                forSalesOrder ? null : reservation.getComponentLine().getLineNo(),
                item.getItemId(),
                item.getCode(),
                item.getName(),
                item.getUnit(),
                reservation.getWarehouse().getWarehouseId(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                reservation.getQuantity(),
                reservation.getConsumedQuantity(),
                reservation.remainingQuantity(),
                reservation.getStatus().name(),
                reservation.getCreatedAt(),
                forSalesOrder ? "SALES_ORDER" : "WORK_ORDER",
                reservation.getSalesOrderId(),
                salesLine != null ? salesLine.salesOrderCode() : null,
                reservation.getSalesOrderLineId(),
                salesLine != null ? salesLine.lineNo() : null);
    }
}
