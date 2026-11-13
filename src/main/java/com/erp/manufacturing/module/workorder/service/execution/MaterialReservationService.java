package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.module.inventory.service.StockBalanceReservationService;
import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialReservationCreateRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialReservationResponse;
import com.erp.manufacturing.module.workorder.dto.execution.WorkOrderReservationReleaseResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.service.WorkOrderVersions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialReservationService {

    private final MaterialReservationRepository reservationRepository;
    private final StockBalanceReservationService stockBalanceReservationService;
    private final OrganizationLookupService organizationLookupService;
    private final WorkOrderExecutionSupport support;
    private final ManufacturingExecutionMapper mapper;
    private final WorkOrderIdempotency idempotencyRecords;

    /**
     * G8a: a component line with a warehouse (D2) is reserved from that warehouse only — another one is
     * refused even when it holds stock (FE §1.4 step 3). A line without one keeps the old rule: any
     * active warehouse of the plant.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RESERVATION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_RESERVED, entityType = "MaterialReservation", entityIdExpression = "reservationId.toString()",
               warehouseId = "#result?.warehouseId()")
    public MaterialReservationResponse reserve(UUID workOrderId, MaterialReservationCreateRequest request,
                                               String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        var replayed = idempotencyRecords.replay(key, WorkOrderCommand.RESERVATION_CREATE, fingerprint);
        if (replayed.isPresent()) {
            return mapper.toResponse(findReservationForWorkOrder(workOrderId, replayed.get()));
        }
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request.expectedWorkOrderVersion(), "expectedWorkOrderVersion");
        support.ensureReservable(workOrder);
        WorkOrderComponentLine componentLine = support.findComponentLine(workOrder, request.componentLineId());
        Warehouse warehouse = support.findActiveWarehouseInPlant(request.warehouseId(), workOrder.getPlant());
        ensureComponentWarehouse(componentLine, warehouse);
        BigDecimal quantity = support.requirePositive(request.quantity(), "Reservation quantity");
        BigDecimal activeReserved = reservationRepository.sumActiveRemainingByComponentLineId(componentLine.getComponentLineId());
        BigDecimal remainingRequirement = componentLine.remainingQuantity().subtract(activeReserved);
        support.ensureDoesNotExceed(quantity, remainingRequirement,
                "Reservation quantity exceeds remaining component requirement");

        StockBalance balance = support.findStockBalance(componentLine.getComponentItem(), warehouse, request.lotId());
        support.ensureAvailableForReservation(balance, quantity);
        MaterialReservation reservation = persistReservation(workOrder, componentLine, balance, quantity);
        idempotencyRecords.record(key, WorkOrderCommand.RESERVATION_CREATE, reservation.getReservationId(), fingerprint);
        support.touchAndFlush(workOrder);
        return mapper.toResponse(reservation);
    }

    /** Moves the quantity from available to reserved on the balance and records the reservation. */
    private MaterialReservation persistReservation(WorkOrder workOrder,
                                                   WorkOrderComponentLine componentLine,
                                                   StockBalance balance,
                                                   BigDecimal quantity) {
        stockBalanceReservationService.reserve(balance, quantity);
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
     * <p>
     * G8a (D2): a line with a warehouse looks in that warehouse only — FE F2: 15 in the right warehouse
     * and 500 in another reserves 15 and leaves 5 short. A line without one still looks across the plant.
     * A replay with the same {@code Idempotency-Key} answers with the work order's current
     * {@code ACTIVE} reservations, not with the list the first call created.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RESERVATION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_RESERVED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public List<MaterialReservationResponse> reserveAutomatically(UUID workOrderId, WorkOrderVersionRequest request,
                                                                  String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.RESERVATION_AUTO, fingerprint).isPresent()) {
            return reservationRepository.findByWorkOrderWorkOrderIdAndStatus(workOrderId, MaterialReservationStatus.ACTIVE)
                    .stream().map(mapper::toResponse).toList();
        }
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request == null ? null : request.expectedWorkOrderVersion(),
                "expectedWorkOrderVersion");
        support.ensureReservable(workOrder);
        idempotencyRecords.record(key, WorkOrderCommand.RESERVATION_AUTO, workOrderId, fingerprint);
        List<UUID> plantWarehouseIds = organizationLookupService
                .resolveScope(ScopeResourceType.PLANT, workOrder.getPlant().getPlantId())
                .warehouseIds();

        // CF-2 (B264): FEFO skips lots past their expiry on this one server clock reading.
        java.time.Instant now = java.time.Instant.now();
        List<MaterialReservation> created = new ArrayList<>();
        for (WorkOrderComponentLine componentLine : workOrder.getComponentLines()) {
            List<UUID> warehouseIds = componentLine.getWarehouse() != null
                    ? List.of(componentLine.getWarehouse().getWarehouseId())
                    : plantWarehouseIds;
            if (warehouseIds.isEmpty()) {
                continue;
            }
            BigDecimal outstanding = componentLine.remainingQuantity().subtract(
                    reservationRepository.sumActiveRemainingByComponentLineId(componentLine.getComponentLineId()));
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            for (StockBalance balance : stockBalanceReservationService.findIssuable(
                    componentLine.getComponentItem().getItemId(), warehouseIds, now)) {
                if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal take = balance.availableQuantity().min(outstanding);
                if (take.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                created.add(persistReservation(workOrder, componentLine, balance, take));
                outstanding = outstanding.subtract(take);
            }
        }
        // Mapped after the flush so every element carries the version this call wrote (G8, D5).
        support.touchAndFlush(workOrder);
        return created.stream().map(mapper::toResponse).toList();
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
    @Auditable(action = AuditAction.MATERIAL_RESERVATION_RELEASED, entityType = "MaterialReservation", entityIdExpression = "reservationId.toString()",
               warehouseId = "#result?.warehouseId()")
    public MaterialReservationResponse release(UUID workOrderId, UUID reservationId, Long expectedWorkOrderVersion,
                                               String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId,
                Map.of("reservationId", reservationId, "expectedWorkOrderVersion",
                        expectedWorkOrderVersion == null ? "" : expectedWorkOrderVersion));
        if (idempotencyRecords.replay(key, WorkOrderCommand.RESERVATION_RELEASE, fingerprint).isPresent()) {
            return mapper.toResponse(findReservationForWorkOrder(workOrderId, reservationId));
        }
        MaterialReservation reservation = findReservationForWorkOrder(workOrderId, reservationId);
        WorkOrderVersions.ensure(reservation.getWorkOrder(), expectedWorkOrderVersion, "expectedWorkOrderVersion");
        if (reservation.getStatus() != MaterialReservationStatus.ACTIVE) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only active reservations can be released");
        }
        idempotencyRecords.record(key, WorkOrderCommand.RESERVATION_RELEASE, reservationId, fingerprint);
        releaseStock(reservation);
        reservation.release();
        MaterialReservation saved = reservationRepository.save(reservation);
        support.touchAndFlush(reservation.getWorkOrder());
        return mapper.toResponse(saved);
    }

    /**
     * Gives the unissued part of every {@code ACTIVE} reservation of the work order back to free stock
     * (G8a, FE §3.3) — the bulk form of {@link #release}. No stock movement: on hand never changed, only
     * reserved did (FE R17). Allowed while the work order is still open, and on a {@code COMPLETED} one too
     * so its leftover can be freed (FE §1.4 step 13); a {@code CANCELLED} or {@code CLOSED} work order
     * already released everything when it got there.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RESERVATION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_RESERVATION_RELEASED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderReservationReleaseResponse releaseAll(UUID workOrderId, WorkOrderVersionRequest request,
                                                          String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.RESERVATION_RELEASE_ALL, fingerprint).isPresent()) {
            WorkOrder current = support.findWorkOrder(workOrderId);
            return new WorkOrderReservationReleaseResponse(workOrderId, current.getVersion(), 0, List.of());
        }
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request == null ? null : request.expectedWorkOrderVersion(),
                "expectedWorkOrderVersion");
        if (workOrder.getStatus() == WorkOrderStatus.CANCELLED || workOrder.getStatus() == WorkOrderStatus.CLOSED) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "A " + workOrder.getStatus() + " work order holds no reservation to release");
        }
        idempotencyRecords.record(key, WorkOrderCommand.RESERVATION_RELEASE_ALL, workOrderId, fingerprint);
        List<WorkOrderReservationReleaseResponse.Line> lines = new ArrayList<>();
        for (MaterialReservation reservation : reservationRepository.findByWorkOrderWorkOrderIdAndStatus(
                workOrderId, MaterialReservationStatus.ACTIVE)) {
            BigDecimal remaining = reservation.remainingQuantity();
            releaseStock(reservation);
            reservation.release();
            reservationRepository.save(reservation);
            lines.add(new WorkOrderReservationReleaseResponse.Line(reservation.getReservationId(),
                    reservation.getComponentLine().getComponentLineId(), reservation.getItem().getItemId(),
                    reservation.getWarehouse().getWarehouseId(),
                    reservation.getLot() == null ? null : reservation.getLot().getLotId(), remaining));
        }
        WorkOrder saved = support.touchAndFlush(workOrder);
        return new WorkOrderReservationReleaseResponse(workOrderId, saved.getVersion(), lines.size(), lines);
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
        // A sales-order reservation (G6a) has no work order at all; it is "not this work order's".
        if (reservation.getWorkOrder() == null
                || !reservation.getWorkOrder().getWorkOrderId().equals(workOrderId)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Reservation does not belong to the work order");
        }
        return reservation;
    }

    /** G8a (D2): a line that names a warehouse is reserved from it and from nowhere else. */
    private void ensureComponentWarehouse(WorkOrderComponentLine componentLine, Warehouse warehouse) {
        if (componentLine.getWarehouse() != null
                && !componentLine.getWarehouse().getWarehouseId().equals(warehouse.getWarehouseId())) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH, Map.of("warehouseId",
                    "the component is reserved from warehouse " + componentLine.getWarehouse().getCode() + " only"));
        }
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
        stockBalanceReservationService.releaseReserved(balance, remaining);
    }
}
