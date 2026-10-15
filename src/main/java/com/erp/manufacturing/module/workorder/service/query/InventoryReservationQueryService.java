package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.dto.InventoryReservationResponse;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only list of work-order reservations held in one warehouse (G4-4). Lives in {@code workorder}
 * because the reservation aggregate does; guarded with the inventory read permission because the
 * question it answers is "why is this stock not free".
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationQueryService {

    private final MaterialReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId)")
    public PageResult<InventoryReservationResponse> list(UUID warehouseId, UUID itemId, UUID lotId,
                                                         MaterialReservationStatus status, Pageable pageable) {
        return PageResult.from(reservationRepository
                .searchByWarehouse(warehouseId, itemId, lotId, status, pageable)
                .map(this::toResponse));
    }

    private InventoryReservationResponse toResponse(MaterialReservation reservation) {
        Item item = reservation.getItem();
        InventoryLot lot = reservation.getLot();
        return new InventoryReservationResponse(
                reservation.getReservationId(),
                reservation.getWorkOrder().getWorkOrderId(),
                reservation.getWorkOrder().getWorkOrderNo(),
                reservation.getWorkOrder().getStatus().name(),
                reservation.getComponentLine().getComponentLineId(),
                reservation.getComponentLine().getLineNo(),
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
                reservation.getCreatedAt());
    }
}
