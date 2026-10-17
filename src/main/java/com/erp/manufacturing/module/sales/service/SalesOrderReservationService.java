package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.model.AuditChangeMode;
import com.erp.manufacturing.common.audit.model.AuditOperation;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.dto.SalesOrderAllocationCandidateResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderAllocationCandidatesResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationReleaseRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationResult;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Stock held for a sales order (G6a, FE R05/R06). This service owns the demand side — order status,
 * which line, how much is still uncovered — and delegates the stock side to
 * {@link SalesOrderStockReservationService} (rule C7).
 *
 * <p>Concurrency: every hold/release checks {@code expectedVersion} against the order and then bumps
 * the order version, so two holds racing for the last uncovered quantity of the same order cannot
 * both commit — the loser's version check or its flush fails with 409. Holds on the same stock
 * position from different orders are serialised by the position's own {@code @Version}.
 *
 * <p>Permission: {@code PERM_SALES_ORDER_MANAGE} on the order's plant (FE Q4) — never the work-order
 * reservation permission.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderReservationService {

    private final SalesOrderRepository salesOrderRepository;
    private final OrganizationLookupService organizationLookupService;
    private final SalesOrderStockReservationService stockReservationService;
    private final SalesOrderDetailAssembler detailAssembler;

    /**
     * Holds {@code quantity} for one line. Allowed while the order can still be planned against
     * ({@code CONFIRMED}/{@code IN_PRODUCTION}/{@code PARTIALLY_FULFILLED}); never more than the
     * line's uncovered quantity ({@code PLANNED_QUANTITY_EXCEEDED}) nor than free stock
     * ({@code INSUFFICIENT_AVAILABLE_STOCK}).
     */
    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_MANAGE', #salesOrderId)")
    @Auditable(action = AuditAction.SALES_ORDER_STOCK_RESERVED, entityType = "SalesOrder", entityId = "#salesOrderId",
               companyId = "#result?.salesOrder()?.companyId()", plantId = "#result?.salesOrder()?.plantId()",
               warehouseId = "#request.warehouseId()",
               changeMode = AuditChangeMode.NONE, operation = AuditOperation.UPDATE)
    public SalesOrderReservationResult reserve(UUID salesOrderId, SalesOrderReservationCreateRequest request) {
        SalesOrder order = findOrder(salesOrderId);
        ensureVersion(order, request.expectedVersion());
        if (!SalesOrderService.PLANNING_ELIGIBLE_STATUSES.contains(order.getStatus())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Stock can only be held for a confirmed sales order, not " + order.getStatus());
        }
        SalesOrderLine line = findLine(order, request.salesOrderLineId());
        Warehouse warehouse = organizationLookupService.getActiveWarehouseInPlant(
                request.warehouseId(), order.getPlant().getPlantId());
        BigDecimal uncovered = detailAssembler.uncoveredQuantity(line);
        if (request.quantity().compareTo(uncovered) > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                    "Quantity exceeds what is still uncovered on the line (" + uncovered.toPlainString() + ")");
        }

        List<MaterialReservation> created = stockReservationService.reserve(
                order.getSalesOrderId(), line.getSalesOrderLineId(), line.getItem(), warehouse,
                request.lotId(), request.quantity());
        bumpVersion(order);
        return new SalesOrderReservationResult(
                created.stream().map(this::toResponse).toList(),
                detailAssembler.toDetail(order));
    }

    /** Releases one ACTIVE hold of this order back to free stock (FE R06). */
    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_MANAGE', #salesOrderId)")
    @Auditable(action = AuditAction.SALES_ORDER_STOCK_RELEASED, entityType = "SalesOrder", entityId = "#salesOrderId",
               companyId = "#result?.salesOrder()?.companyId()", plantId = "#result?.salesOrder()?.plantId()",
               changeMode = AuditChangeMode.NONE, operation = AuditOperation.UPDATE)
    public SalesOrderReservationResult release(UUID salesOrderId,
                                               UUID reservationId,
                                               SalesOrderReservationReleaseRequest request) {
        SalesOrder order = findOrder(salesOrderId);
        ensureVersion(order, request.expectedVersion());
        MaterialReservation released = stockReservationService.release(order.getSalesOrderId(), reservationId);
        bumpVersion(order);
        return new SalesOrderReservationResult(List.of(toResponse(released)), detailAssembler.toDetail(order));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_READ', #salesOrderId)")
    public PageResult<SalesOrderReservationResponse> list(UUID salesOrderId,
                                                          UUID salesOrderLineId,
                                                          MaterialReservationStatus status,
                                                          Pageable pageable) {
        findOrder(salesOrderId);
        return PageResult.from(stockReservationService
                .list(salesOrderId, salesOrderLineId, status, pageable)
                .map(this::toResponse));
    }

    /**
     * Where a line could be held from (FE Q6: read with the sales permission, not inventory READ —
     * only positions of this line's item in this order's plant are returned). {@code warehouseId}
     * narrows to one warehouse of the plant; without it every warehouse of the plant is searched.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_READ', #salesOrderId)")
    public SalesOrderAllocationCandidatesResponse candidates(UUID salesOrderId,
                                                             UUID salesOrderLineId,
                                                             UUID warehouseId,
                                                             Pageable pageable) {
        SalesOrder order = findOrder(salesOrderId);
        SalesOrderLine line = findLine(order, salesOrderLineId);
        List<UUID> warehouseIds = warehouseId != null
                ? List.of(organizationLookupService
                        .getActiveWarehouseInPlant(warehouseId, order.getPlant().getPlantId()).getWarehouseId())
                : organizationLookupService
                        .resolveScope(ScopeResourceType.PLANT, order.getPlant().getPlantId()).warehouseIds();
        PageResult<SalesOrderAllocationCandidateResponse> positions = warehouseIds.isEmpty()
                ? PageResult.from(org.springframework.data.domain.Page.empty(pageable))
                : PageResult.from(stockReservationService
                        .findCandidates(line.getItem().getItemId(), warehouseIds, pageable)
                        .map(SalesOrderReservationService::toCandidate));
        return new SalesOrderAllocationCandidatesResponse(
                line.getSalesOrderLineId(),
                line.getItem().getItemId(),
                line.getItem().getUnit(),
                detailAssembler.uncoveredQuantity(line),
                positions);
    }

    private SalesOrder findOrder(UUID salesOrderId) {
        return salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales order", salesOrderId));
    }

    private static SalesOrderLine findLine(SalesOrder order, UUID salesOrderLineId) {
        return order.getLines().stream()
                .filter(line -> line.getSalesOrderLineId().equals(salesOrderLineId))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales order line", salesOrderLineId));
    }

    private static void ensureVersion(SalesOrder order, Long expectedVersion) {
        if (!Objects.equals(expectedVersion, order.getVersion())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Sales order was modified by another request");
        }
    }

    /**
     * Holds live on a child table, so without this the order row is not dirty and its version would
     * not move — a blind retry of an applied hold would then pass the version check again (same trap
     * as {@code B112} #2). One dirty audited field, one UPDATE, exactly one bump.
     */
    private void bumpVersion(SalesOrder order) {
        order.setUpdatedAt(Instant.now());
        salesOrderRepository.saveAndFlush(order);
    }

    private SalesOrderReservationResponse toResponse(MaterialReservation reservation) {
        InventoryLot lot = reservation.getLot();
        return new SalesOrderReservationResponse(
                reservation.getReservationId(),
                reservation.getSalesOrderId(),
                reservation.getSalesOrderLineId(),
                reservation.getItem().getItemId(),
                reservation.getItem().getCode(),
                reservation.getItem().getUnit(),
                reservation.getWarehouse().getWarehouseId(),
                reservation.getWarehouse().getCode(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                lot != null ? lot.getExpiresAt() : null,
                reservation.getQuantity(),
                reservation.getConsumedQuantity(),
                reservation.remainingQuantity(),
                reservation.getStatus().name(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }

    private static SalesOrderAllocationCandidateResponse toCandidate(StockBalance balance) {
        InventoryLot lot = balance.getLot();
        return new SalesOrderAllocationCandidateResponse(
                balance.getBalanceId(),
                balance.getWarehouse().getWarehouseId(),
                balance.getWarehouse().getCode(),
                balance.getWarehouse().getName(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                lot != null ? lot.getExpiresAt() : null,
                balance.getQuantity(),
                balance.availableQuantity(),
                balance.getVersion());
    }
}
