package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.*;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.service.RoutingLookupService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.mapper.WorkOrderMapper;
import com.erp.manufacturing.module.workorder.repository.ComponentQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionReceiptService;
import com.erp.manufacturing.module.workorder.service.execution.WipTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final BomLookupService bomLookupService;
    private final RoutingLookupService routingLookupService;
    private final MaterialReservationService materialReservationService;
    private final MaterialIssueService materialIssueService;
    private final WipTransactionService wipTransactionService;
    private final ProductionReceiptService productionReceiptService;
    private final WorkOrderDemandAllocationService allocationService;
    private final MaterialReservationRepository reservationRepository;
    private final WorkOrderReleaseGate releaseGate;
    private final WorkOrderMapper mapper;

    /**
     * Manual creation. An ACTIVE routing is snapshotted when the item has one, but is not required
     * — only the MRP proposal path enforces {@code MISSING_ROUTING} in F4
     * (see {@link #createFromMrp}).
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.WORK_ORDER_CREATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse create(UUID plantId, WorkOrderCreateRequest request) {
        return toResponse(createInternal(plantId, request, false));
    }

    /**
     * Converting an approved MAKE proposal requires an ACTIVE routing: spec §8.1 blocks the
     * proposal with {@code MISSING_ROUTING} rather than letting a work order exist that
     * Production Execution cannot report operations against.
     *
     * @param salesOrderLineId the sales order line the proposal's demand traces back to, or
     *                         {@code null} when the demand was {@code MANUAL}/{@code FORECAST} or
     *                         came from a component level. Non-null earmarks this work order's
     *                         output for that line (spec §2.4).
     * @param lineage          which run and proposal produced this work order (spec §3.3 "Nguồn
     *                         gốc", F8). Deliberately a parameter rather than a field on
     *                         {@link WorkOrderCreateRequest}: lineage is something the server knows,
     *                         not something a client declares — the same call F6 made for
     *                         {@code salesOrderLineId} (CLAUDE.md §0.11 item 1).
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SUPPLY_SUGGESTION_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.WORK_ORDER_CREATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse createFromMrp(UUID plantId,
                                           WorkOrderCreateRequest request,
                                           UUID salesOrderLineId,
                                           PlanningLineage lineage) {
        WorkOrder workOrder = createInternal(plantId, request, true);
        if (lineage != null) {
            workOrder.setPlanningRunId(lineage.runId());
            workOrder.setPlanningRunCode(lineage.runCode());
            workOrder.setPlanningProposalId(lineage.proposalId());
        }
        allocationService.allocate(workOrder, salesOrderLineId);
        return toResponse(workOrder);
    }

    /**
     * Where a work order came from in planning (spec §3.3). {@code runCode} is carried by value, not
     * looked up later, for the same reason {@code sourceRoutingCode} is (B49): it records what the
     * run was called at conversion time.
     */
    public record PlanningLineage(UUID runId, String runCode, UUID proposalId) {}

    private WorkOrder createInternal(UUID plantId, WorkOrderCreateRequest request, boolean routingRequired) {
        Plant plant = organizationLookupService.getActivePlant(plantId);
        String workOrderNo = normalizeCode(request.workOrderNo(), "Work order number");
        if (workOrderRepository.existsByPlantPlantIdAndWorkOrderNo(plantId, workOrderNo)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Work order number", workOrderNo);
        }

        Item product = itemLookupService.getActiveItem(request.productItemId());
        ensureManufacturableProduct(product);
        ensureProductBelongsToPlantCompany(product, plant);
        Warehouse outputWarehouse = organizationLookupService.getActiveWarehouseInPlant(request.outputWarehouseId(), plantId);
        BigDecimal plannedQuantity = requirePositive(request.plannedQuantity(), "Planned quantity");
        ensureDateRange(request.plannedStartAt(), request.plannedEndAt());

        BomHeader bom = bomLookupService.getActiveBom(plant.getCompany().getCompanyId(), product.getItemId());
        WorkOrder workOrder = WorkOrder.builder()
                .company(plant.getCompany())
                .plant(plant)
                .workOrderNo(workOrderNo)
                .productItem(product)
                .bom(bom)
                .bomRevision(bom.getRevision())
                .outputWarehouse(outputWarehouse)
                .plannedQuantity(plannedQuantity)
                .completedQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.DRAFT)
                .plannedStartAt(request.plannedStartAt())
                .plannedEndAt(request.plannedEndAt())
                .notes(trimToNull(request.notes()))
                .build();
        snapshotComponentLines(workOrder, bom, plannedQuantity);
        snapshotRouting(workOrder, plant, product, routingRequired);
        return workOrderRepository.save(workOrder);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_READ', 'PLANT', #plantId)")
    public PageResult<WorkOrderResponse> list(UUID plantId,
                                              WorkOrderStatus status,
                                              UUID productItemId,
                                              String search,
                                              Pageable pageable) {
        organizationLookupService.getActivePlant(plantId);
        Page<WorkOrder> page = workOrderRepository.search(
                plantId, status, productItemId, trimToNull(search), pageable);
        // One allocation lookup and one reservation lookup for the whole page, never one per row
        // (rule C14). Both must stay outside page.map(...) — inside it they become N+1 while
        // producing byte-identical output, which is exactly the failure C15 asks to be asserted.
        List<UUID> workOrderIds = page.getContent().stream().map(WorkOrder::getWorkOrderId).toList();
        Map<UUID, List<WorkOrderDemandAllocationResponse>> allocations =
                allocationService.findByWorkOrderIds(workOrderIds);
        Map<UUID, BigDecimal> reserved = reservedByComponentLine(workOrderIds);
        return PageResult.from(page.map(workOrder -> mapper.toResponse(
                workOrder, allocations.getOrDefault(workOrder.getWorkOrderId(), List.of()), reserved)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_READ', #workOrderId)")
    public WorkOrderResponse get(UUID workOrderId) {
        return toResponse(findWorkOrder(workOrderId));
    }

    /**
     * Only {@code DRAFT} work orders may be updated — {@code BLOCKED} is deliberately excluded.
     * A blocked work order already has reservations attached to its component lines; changing
     * {@code plannedQuantity} recalculates {@code requiredQuantity} and could make
     * {@code required < reserved}, breaking that invariant. Release the reservations first.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_UPDATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse update(UUID workOrderId, WorkOrderUpdateRequest request) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        ensureDraft(workOrder);

        if (request.outputWarehouseId() != null) {
            workOrder.setOutputWarehouse(organizationLookupService.getActiveWarehouseInPlant(
                    request.outputWarehouseId(), workOrder.getPlant().getPlantId()));
        }
        if (request.plannedQuantity() != null) {
            BigDecimal plannedQuantity = requirePositive(request.plannedQuantity(), "Planned quantity");
            workOrder.setPlannedQuantity(plannedQuantity);
            recalculateRequirements(workOrder, plannedQuantity);
        }
        if (request.plannedStartAt() != null) {
            workOrder.setPlannedStartAt(request.plannedStartAt());
        }
        if (request.plannedEndAt() != null) {
            workOrder.setPlannedEndAt(request.plannedEndAt());
        }
        ensureDateRange(workOrder.getPlannedStartAt(), workOrder.getPlannedEndAt());
        if (request.notes() != null) {
            workOrder.setNotes(trimToNull(request.notes()));
        }
        return toResponse(workOrderRepository.save(workOrder));
    }

    /**
     * Schedules a draft (spec §3.1). Nothing is reserved and no material moves — {@code PLANNED}
     * only records that a planner has committed to the dates, which is what the planning board
     * filters on. Release still goes through the material gate.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_UPDATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse plan(UUID workOrderId) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        if (!workOrder.canPlan()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only draft work orders can be planned");
        }
        workOrder.plan();
        return toResponse(workOrderRepository.save(workOrder));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_RELEASED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse release(UUID workOrderId) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        ensureReleasable(workOrder);
        if (workOrder.getComponentLines().isEmpty()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Cannot release work order without component requirements");
        }
        releaseGate.ensureMaterialReady(workOrderId);
        workOrder.release(Instant.now());
        WorkOrder saved = workOrderRepository.save(workOrder);
        wipTransactionService.recordStart(saved);
        return toResponse(saved);
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_CANCELLED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse cancel(UUID workOrderId, WorkOrderCancelRequest request) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        // Fail before any state check (C9): a caller who forgot the reason should be told that,
        // not told the work order is in the wrong status.
        String reason = trimToNull(request == null ? null : request.reason());
        if (reason == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.APPROVAL_REASON_REQUIRED,
                    "Cancel reason is required");
        }
        if (workOrder.getStatus() != WorkOrderStatus.DRAFT
                && workOrder.getStatus() != WorkOrderStatus.PLANNED
                && workOrder.getStatus() != WorkOrderStatus.BLOCKED
                && workOrder.getStatus() != WorkOrderStatus.RELEASED) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only draft, planned, blocked, or released work orders can be cancelled");
        }
        if (hasAnyMovement(workOrder)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Work order with issued or produced quantity cannot be cancelled");
        }
        materialReservationService.cancelActiveReservations(workOrder);
        workOrder.cancel(Instant.now(), reason);
        return toResponse(workOrderRepository.save(workOrder));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_EXECUTE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_COMPONENT_ISSUED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse issueComponent(UUID workOrderId,
                                            WorkOrderComponentIssueRequest request,
                                            String idempotencyKey) {
        materialIssueService.postInternal(workOrderId, new MaterialIssuePostRequest(null, List.of(
                new MaterialIssueLineRequest(
                        request.componentLineId(),
                        null,
                        request.warehouseId(),
                        request.lotId(),
                        request.lotCode(),
                        request.quantity(),
                        request.reason(),
                        null))), idempotencyKey);
        return toResponse(findWorkOrder(workOrderId));
    }

    /**
     * Submits a production receipt for approval — it does NOT complete the work order.
     * Stock and completed quantity only change once the receipt is approved.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_EXECUTE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_SUBMITTED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse completeOutput(UUID workOrderId,
                                            WorkOrderOutputCompletionRequest request,
                                            String idempotencyKey) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        productionReceiptService.postInternal(workOrderId, new ProductionReceiptPostRequest(
                workOrder.getOutputWarehouse().getWarehouseId(),
                request.lotId(),
                request.lotCode(),
                request.quantity(),
                request.reason(),
                null), idempotencyKey);
        return toResponse(findWorkOrder(workOrderId));
    }

    /**
     * Every single-work-order response goes through here so {@code allocations[]} is present on
     * mutations too, not only on the read endpoints — the frontend re-renders the same card from
     * whatever the last call returned.
     */
    private WorkOrderResponse toResponse(WorkOrder workOrder) {
        List<UUID> ids = List.of(workOrder.getWorkOrderId());
        return mapper.toResponse(
                workOrder,
                allocationService.findByWorkOrderIds(ids).getOrDefault(workOrder.getWorkOrderId(), List.of()),
                reservedByComponentLine(ids));
    }

    /**
     * Remaining {@code ACTIVE} reservation per component line, for however many work orders are being
     * rendered — one query either way (rule C14).
     *
     * <p>Keyed by {@code componentLineId} alone, with no work order dimension: component line ids are
     * globally unique, so a flat map is unambiguous across a whole page.
     */
    private Map<UUID, BigDecimal> reservedByComponentLine(List<UUID> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        return reservationRepository.sumActiveRemainingByWorkOrderIds(workOrderIds).stream()
                .collect(Collectors.toMap(
                        ComponentQuantityProjection::getComponentLineId,
                        ComponentQuantityProjection::getQuantity));
    }

    private WorkOrder findWorkOrder(UUID workOrderId) {
        return workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId));
    }

    private void ensureManufacturableProduct(Item product) {
        if (product.getType() != ItemType.WIP && product.getType() != ItemType.FINISHED_GOOD) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Work order product must be WIP or FINISHED_GOOD");
        }
    }

    private void ensureProductBelongsToPlantCompany(Item product, Plant plant) {
        if (!product.getCompany().getCompanyId().equals(plant.getCompany().getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Product item must belong to the plant company");
        }
    }

    private void ensureDraft(WorkOrder workOrder) {
        if (!workOrder.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only draft work orders can be changed");
        }
    }

    private void ensureReleasable(WorkOrder workOrder) {
        if (!workOrder.canRelease()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only draft, planned, or blocked work orders can be released");
        }
    }

    private void snapshotComponentLines(WorkOrder workOrder, BomHeader bom, BigDecimal plannedQuantity) {
        for (BomLine bomLine : bom.getLines()) {
            WorkOrderComponentLine line = WorkOrderComponentLine.builder()
                    .workOrder(workOrder)
                    .bomLine(bomLine)
                    .componentItem(bomLine.getComponentItem())
                    .lineNo(bomLine.getLineNo())
                    .quantityPer(bomLine.getQuantityPer())
                    .scrapRate(bomLine.getScrapRate())
                    .requiredQuantity(requiredQuantity(plannedQuantity, bomLine))
                    .issuedQuantity(BigDecimal.ZERO)
                    .build();
            workOrder.getComponentLines().add(line);
        }
    }

    /**
     * Freezes the item's ACTIVE routing onto the work order (spec §3.3). The code and version are
     * <em>copied</em>, never read back through an association, so later revisions of the routing
     * master leave existing work orders untouched — the same contract as
     * {@link #snapshotComponentLines} (invariant B49).
     */
    private void snapshotRouting(WorkOrder workOrder, Plant plant, Item product, boolean routingRequired) {
        UUID companyId = plant.getCompany().getCompanyId();
        RoutingHeader routing = routingRequired
                ? routingLookupService.getActiveRouting(companyId, product.getItemId())
                : routingLookupService.findActiveRouting(companyId, product.getItemId()).orElse(null);
        if (routing == null) {
            return;
        }
        workOrder.setSourceRoutingId(routing.getRoutingId());
        workOrder.setSourceRoutingCode(routing.getCode());
        workOrder.setSourceRoutingVersion(routing.getRoutingVersion());
        workOrder.setRoutingCapturedAt(Instant.now());

        // F5: the operations themselves are copied too, so Production Execution can report against
        // a stable list of steps. Same contract as the header fields — values, not a live read.
        for (RoutingOperation operation : routing.getOperations()) {
            workOrder.getOperations().add(WorkOrderOperation.builder()
                    .workOrder(workOrder)
                    .sourceRoutingOperationId(operation.getRoutingOperationId())
                    .sequence(operation.getSequence())
                    .name(operation.getName())
                    .workCenterCode(operation.getWorkCenterCode())
                    .setupMinutes(operation.getSetupMinutes())
                    .runMinutesPerUnit(operation.getRunMinutesPerUnit())
                    .build());
        }
    }

    private void recalculateRequirements(WorkOrder workOrder, BigDecimal plannedQuantity) {
        for (WorkOrderComponentLine line : workOrder.getComponentLines()) {
            line.setRequiredQuantity(plannedQuantity
                    .multiply(line.getQuantityPer())
                    .multiply(BigDecimal.ONE.add(line.getScrapRate())));
        }
    }

    private BigDecimal requiredQuantity(BigDecimal plannedQuantity, BomLine bomLine) {
        return plannedQuantity
                .multiply(bomLine.getQuantityPer())
                .multiply(BigDecimal.ONE.add(bomLine.getScrapRate()));
    }

    private WorkOrderComponentLine findComponentLine(WorkOrder workOrder, UUID componentLineId) {
        return workOrder.getComponentLines().stream()
                .filter(line -> line.getComponentLineId().equals(componentLineId))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order component line", componentLineId));
    }

    /**
     * Since F5 a work order can have shop-floor output without any receipt, so cancellation has to
     * look at {@code actualGoodQuantity} as well — otherwise a work order that produced 100 units
     * but has not been receipted yet would still look untouched.
     */
    private boolean hasAnyMovement(WorkOrder workOrder) {
        return workOrder.getCompletedQuantity().compareTo(BigDecimal.ZERO) > 0
                || workOrder.getActualGoodQuantity().compareTo(BigDecimal.ZERO) > 0
                || workOrder.getActualScrapQuantity().compareTo(BigDecimal.ZERO) > 0
                || workOrder.getComponentLines().stream()
                .anyMatch(line -> line.getIssuedQuantity().compareTo(BigDecimal.ZERO) > 0);
    }

    private BigDecimal remainingCompletionQuantity(WorkOrder workOrder) {
        return workOrder.getPlannedQuantity().subtract(workOrder.getCompletedQuantity());
    }

    private void ensureDoesNotExceed(BigDecimal quantity, BigDecimal remaining, String message) {
        if (quantity.compareTo(remaining) > 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED, message);
        }
    }

    private BigDecimal requirePositive(BigDecimal quantity, String fieldName) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " must be greater than zero");
        }
        return quantity;
    }

    private void ensureDateRange(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Planned end date cannot be before planned start date");
        }
    }

    private String normalizeCode(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
