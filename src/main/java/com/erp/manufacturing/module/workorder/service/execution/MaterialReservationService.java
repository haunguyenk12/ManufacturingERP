package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialReservationCreateRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialReservationResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialReservationService {

    private final MaterialReservationRepository reservationRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final WorkOrderExecutionSupport support;
    private final ManufacturingExecutionMapper mapper;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RESERVATION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_RESERVED, entityType = "MaterialReservation", entityIdExpression = "reservationId.toString()")
    public MaterialReservationResponse reserve(UUID workOrderId, MaterialReservationCreateRequest request) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureExecutable(workOrder);
        WorkOrderComponentLine componentLine = support.findComponentLine(workOrder, request.componentLineId());
        Warehouse warehouse = support.findActiveWarehouseInPlant(request.warehouseId(), workOrder.getPlant());
        BigDecimal quantity = support.requirePositive(request.quantity(), "Reservation quantity");
        BigDecimal activeReserved = reservationRepository.sumActiveRemainingByComponentLineId(componentLine.getComponentLineId());
        BigDecimal remainingRequirement = componentLine.remainingQuantity().subtract(activeReserved);
        support.ensureDoesNotExceed(quantity, remainingRequirement,
                "Reservation quantity exceeds remaining component requirement");

        StockBalance balance = support.findStockBalance(componentLine.getComponentItem(), warehouse, request.lotId());
        support.ensureAvailableForReservation(balance, quantity);
        balance.reserve(quantity);
        stockBalanceRepository.save(balance);

        MaterialReservation reservation = MaterialReservation.builder()
                .workOrder(workOrder)
                .componentLine(componentLine)
                .item(componentLine.getComponentItem())
                .warehouse(warehouse)
                .lot(balance.getLot())
                .quantity(quantity)
                .consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE)
                .build();
        return mapper.toResponse(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RESERVATION_MANAGE', #workOrderId)")
    public PageResult<MaterialReservationResponse> list(UUID workOrderId, Pageable pageable) {
        support.findWorkOrder(workOrderId);
        return PageResult.from(reservationRepository.findByWorkOrderWorkOrderId(workOrderId, pageable)
                .map(mapper::toResponse));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RESERVATION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_RESERVATION_RELEASED, entityType = "MaterialReservation", entityIdExpression = "reservationId.toString()")
    public MaterialReservationResponse release(UUID workOrderId, UUID reservationId) {
        MaterialReservation reservation = findReservationForWorkOrder(workOrderId, reservationId);
        if (reservation.getStatus() != MaterialReservationStatus.ACTIVE) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only active reservations can be released");
        }
        releaseStock(reservation);
        reservation.release();
        return mapper.toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public void cancelActiveReservations(WorkOrder workOrder) {
        List<MaterialReservation> reservations = reservationRepository.findByWorkOrderWorkOrderIdAndStatus(
                workOrder.getWorkOrderId(), MaterialReservationStatus.ACTIVE);
        for (MaterialReservation reservation : reservations) {
            releaseStock(reservation);
            reservation.cancel();
            reservationRepository.save(reservation);
        }
    }

    MaterialReservation findActiveReservationForIssue(UUID workOrderId, UUID reservationId) {
        MaterialReservation reservation = findReservationForWorkOrder(workOrderId, reservationId);
        if (reservation.getStatus() != MaterialReservationStatus.ACTIVE) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Reservation is not active");
        }
        return reservation;
    }

    private MaterialReservation findReservationForWorkOrder(UUID workOrderId, UUID reservationId) {
        MaterialReservation reservation = reservationRepository.findWithDetailsByReservationId(reservationId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Material reservation", reservationId));
        if (!reservation.getWorkOrder().getWorkOrderId().equals(workOrderId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Reservation does not belong to the work order");
        }
        return reservation;
    }

    private void releaseStock(MaterialReservation reservation) {
        BigDecimal remaining = reservation.remainingQuantity();
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        StockBalance balance = support.findStockBalance(
                reservation.getItem(),
                reservation.getWarehouse(),
                reservation.getLot() != null ? reservation.getLot().getLotId() : null);
        balance.releaseReserved(remaining);
        stockBalanceRepository.save(balance);
    }
}

