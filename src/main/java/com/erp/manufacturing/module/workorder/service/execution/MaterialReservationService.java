package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialReservationService {

    private final MaterialReservationRepository reservationRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final OrganizationLookupService organizationLookupService;
    private final WorkOrderExecutionSupport support;
    private final ManufacturingExecutionMapper mapper;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RESERVATION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_RESERVED, entityType = "MaterialReservation", entityIdExpression = "reservationId.toString()")
    public MaterialReservationResponse reserve(UUID workOrderId, MaterialReservationCreateRequest request) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureReservable(workOrder);
        WorkOrderComponentLine componentLine = support.findComponentLine(workOrder, request.componentLineId());
        Warehouse warehouse = support.findActiveWarehouseInPlant(request.warehouseId(), workOrder.getPlant());
        BigDecimal quantity = support.requirePositive(request.quantity(), "Reservation quantity");
        BigDecimal activeReserved = reservationRepository.sumActiveRemainingByComponentLineId(componentLine.getComponentLineId());
        BigDecimal remainingRequirement = componentLine.remainingQuantity().subtract(activeReserved);
        support.ensureDoesNotExceed(quantity, remainingRequirement,
                "Reservation quantity exceeds remaining component requirement");

        StockBalance balance = support.findStockBalance(componentLine.getComponentItem(), warehouse, request.lotId());
        support.ensureAvailableForReservation(balance, quantity);
        return mapper.toResponse(persistReservation(workOrder, componentLine, balance, quantity));
    }

    /** Moves the quantity from available to reserved on the balance and records the reservation. */
    private MaterialReservation persistReservation(WorkOrder workOrder,
                                                   WorkOrderComponentLine componentLine,
                                                   StockBalance balance,
                                                   BigDecimal quantity) {
        balance.reserve(quantity);
        stockBalanceRepository.save(balance);
        return reservationRepository.save(MaterialReservation.builder()
                .workOrder(workOrder)
                .componentLine(componentLine)
                .item(componentLine.getComponentItem())
                .warehouse(balance.getWarehouse())
                .lot(balance.getLot())
                .quantity(quantity)
                .consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE)
                .build());
    }

    /**
     * Reserves every short component automatically, picking stock FEFO (spec §4.2). Complements —
     * does not replace — {@link #reserve}, which stays the fallback when the planner needs to choose
     * a specific warehouse or lot.
     * <p>
     * Partial success is deliberate: a component with no stock left simply gets no reservation and
     * the work order stays un-releasable. Failing the whole call would throw away the reservations
     * that <em>could</em> be made, which is worse for the shop floor than a partial result the
     * readiness endpoint already reports honestly.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RESERVATION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_RESERVED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public List<MaterialReservationResponse> reserveAutomatically(UUID workOrderId) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureReservable(workOrder);
        List<UUID> warehouseIds = organizationLookupService
                .resolveScope(ScopeResourceType.PLANT, workOrder.getPlant().getPlantId())
                .warehouseIds();
        if (warehouseIds.isEmpty()) {
            return List.of();
        }

        List<MaterialReservationResponse> created = new ArrayList<>();
        for (WorkOrderComponentLine componentLine : workOrder.getComponentLines()) {
            BigDecimal outstanding = componentLine.remainingQuantity().subtract(
                    reservationRepository.sumActiveRemainingByComponentLineId(componentLine.getComponentLineId()));
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            for (StockBalance balance : stockBalanceRepository.findIssuableBalancesFefo(
                    componentLine.getComponentItem().getItemId(), warehouseIds, LotStatus.AVAILABLE)) {
                if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal take = balance.availableQuantity().min(outstanding);
                if (take.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                created.add(mapper.toResponse(persistReservation(workOrder, componentLine, balance, take)));
                outstanding = outstanding.subtract(take);
            }
        }
        return created;
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
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
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
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Reservation is not active");
        }
        return reservation;
    }

    /**
     * Finds the reservation without judging its status — deliberately package-private for
     * {@code MaterialIssueService.postFlat}, which needs the component line and warehouse only to
     * translate the flat request before the idempotency replay check runs. Enforcing {@code ACTIVE}
     * there made a retry of an already-issued reservation fail instead of replaying (debt #24);
     * {@code postNew} still enforces it for anything that actually creates a document.
     */
    MaterialReservation findReservationForWorkOrder(UUID workOrderId, UUID reservationId) {
        MaterialReservation reservation = reservationRepository.findWithDetailsByReservationId(reservationId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Material reservation", reservationId));
        if (!reservation.getWorkOrder().getWorkOrderId().equals(workOrderId)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
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

