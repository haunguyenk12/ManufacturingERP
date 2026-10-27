package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
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
import com.erp.manufacturing.module.planning.service.WorkOrderPlanningLinkService;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCommand;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.mapper.WorkOrderMapper;
import com.erp.manufacturing.module.workorder.repository.ComponentQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostBaselineService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final WorkOrderScheduler scheduler;
    private final MaterialIssueRepository materialIssueRepository;
    private final WorkOrderPlanningLinkService planningLinkService;
    private final WorkOrderIdempotency idempotencyRecords;
    private final SecurityAuditorAware auditorAware;
    private final ProductionCostBaselineService costBaselineService;
    private final WorkOrderMapper mapper;

    /**
     * Manual creation. An ACTIVE routing is snapshotted when the item has one, but is not required
     * — only the MRP proposal path enforces {@code MISSING_ROUTING} in F4
     * (see {@link #createFromMrp}).
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.WORK_ORDER_CREATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
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
    @Auditable(action = AuditAction.WORK_ORDER_CREATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public WorkOrderResponse createFromMrp(UUID plantId,
                                           WorkOrderCreateRequest request,
                                           UUID salesOrderLineId,
                                           PlanningLineage lineage) {
        return createFromMrp(plantId, request, salesOrderLineId, lineage, null);
    }

    /**
     * @param allocationCap the most this work order may cover on {@code salesOrderLineId} — the
     *                      line's uncovered quantity read in the caller's transaction (G6b, B181).
     *                      {@code null} keeps the original cap, the line's open quantity.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SUPPLY_SUGGESTION_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.WORK_ORDER_CREATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public WorkOrderResponse createFromMrp(UUID plantId,
                                           WorkOrderCreateRequest request,
                                           UUID salesOrderLineId,
                                           PlanningLineage lineage,
                                           BigDecimal allocationCap) {
        WorkOrder workOrder = createInternal(plantId, request, true);
        if (lineage != null) {
            workOrder.setPlanningRunId(lineage.runId());
            workOrder.setPlanningRunCode(lineage.runCode());
            workOrder.setPlanningProposalId(lineage.proposalId());
        }
        allocationService.allocate(workOrder, salesOrderLineId, allocationCap);
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
        applyComponentWarehouses(workOrder, request.componentWarehouses());
        snapshotRouting(workOrder, plant, product, routingRequired);
        return workOrderRepository.save(workOrder);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_READ', 'PLANT', #plantId)")
    public PageResult<WorkOrderResponse> list(UUID plantId,
                                              WorkOrderStatus status,
                                              UUID productItemId,
                                              String search,
                                              LocalDate fromDate,
                                              LocalDate toDate,
                                              Pageable pageable) {
        Plant plant = organizationLookupService.getActivePlant(plantId);
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("toDate", "must not be before fromDate"));
        }
        // G8a: the dates are the plant's calendar days, so a work order created at 23:30 local time lands
        // on that local day, not on the next UTC one. Half-open [from 00:00, to + 1 day 00:00).
        Instant createdFrom = fromDate == null ? null : fromDate.atStartOfDay(ZoneId.of(plant.getTimezone())).toInstant();
        Instant createdTo = toDate == null ? null
                : toDate.plusDays(1).atStartOfDay(ZoneId.of(plant.getTimezone())).toInstant();
        Page<WorkOrder> page = workOrderRepository.search(
                plantId, status, productItemId, trimToNull(search), createdFrom, createdTo, pageable);
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
     *
     * <p>G8: {@code expectedVersion} is required; a source-linked draft refuses a new quantity or output
     * warehouse with {@code SOURCE_LOCKED} (FE Q7) — those are what the planning quota and the sales
     * order allocation were sized on. Component warehouses stay editable on a source-linked draft: the
     * conversion never captured any, so there is nothing of the source's to protect.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_UPDATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public WorkOrderResponse update(UUID workOrderId, WorkOrderUpdateRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.WORK_ORDER_UPDATE, fingerprint).isPresent()) {
            return toResponse(findWorkOrder(workOrderId));
        }
        WorkOrder workOrder = findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request.expectedVersion(), "expectedVersion");
        ensureDraft(workOrder);
        ensureSourceFieldsUntouched(workOrder, request);
        idempotencyRecords.record(key, WorkOrderCommand.WORK_ORDER_UPDATE, workOrderId, fingerprint);

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
        applyComponentWarehouses(workOrder, request.componentWarehouses());
        workOrder.touch();
        return toResponse(workOrderRepository.saveAndFlush(workOrder));
    }

    /**
     * Schedules a draft (spec §3.1). Nothing is reserved and no material moves — {@code PLANNED}
     * only records that a planner has committed to the dates, which is what the planning board
     * filters on. Release still goes through the material gate.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_UPDATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public WorkOrderResponse plan(UUID workOrderId, WorkOrderPlanRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.WORK_ORDER_PLAN, fingerprint).isPresent()) {
            return toResponse(findWorkOrder(workOrderId));
        }
        WorkOrder workOrder = findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request == null ? null : request.expectedWorkOrderVersion(),
                "expectedWorkOrderVersion");
        if (!workOrder.canPlan()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only draft work orders can be planned");
        }
        idempotencyRecords.record(key, WorkOrderCommand.WORK_ORDER_PLAN, workOrderId, fingerprint);
        applyComponentWarehouses(workOrder, request.componentWarehouses());
        workOrder.plan();
        return toResponse(workOrderRepository.saveAndFlush(workOrder));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_RELEASED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public WorkOrderResponse release(UUID workOrderId, WorkOrderVersionRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.WORK_ORDER_RELEASE, fingerprint).isPresent()) {
            return toResponse(findWorkOrder(workOrderId));
        }
        WorkOrder workOrder = findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request == null ? null : request.expectedWorkOrderVersion(),
                "expectedWorkOrderVersion");
        ensureReleasable(workOrder);
        if (workOrder.getComponentLines().isEmpty()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Cannot release work order without component requirements");
        }
        idempotencyRecords.record(key, WorkOrderCommand.WORK_ORDER_RELEASE, workOrderId, fingerprint);
        releaseGate.ensureMaterialReady(workOrderId);
        Instant now = Instant.now();
        workOrder.release(now);
        scheduleOperations(workOrder, now);
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        wipTransactionService.recordStart(saved);
        // G11a (FE R05): the cost baseline is captured once, in this transaction; missing prices never block (D4).
        costBaselineService.capture(saved, now.truncatedTo(ChronoUnit.MICROS));
        return toResponse(saved);
    }

    /**
     * C2-8 decision #1: the schedule is generated exactly once, here, at release — not at creation
     * or at {@link #plan}. Sequential forward scheduling per operation (sequence order, already
     * guaranteed by {@code @OrderBy} on {@link WorkOrder#getOperations()}): each operation starts
     * when the previous one ends (or at the work order's own {@code plannedStartAt}/now for the
     * first), and walks the operation's Work Center calendar (if any) to skip non-working time,
     * exactly like {@code ProductionExecutionService}/{@code MrpCalculationService} reuse other
     * modules' lookup services rather than duplicating their logic (rule C7).
     *
     * <p>The formula itself lives in {@link WorkOrderScheduler} (G8a), shared with the work order's
     * schedule preview so the preview can never promise what release would not commit. G8a also
     * records which schedule this is (D9): the Capacity Board reads these operations directly, and the
     * revision lets a client tell the committed schedule from one it previewed earlier.
     */
    private void scheduleOperations(WorkOrder workOrder, Instant now) {
        Instant start = workOrder.getPlannedStartAt() != null ? workOrder.getPlannedStartAt() : now;
        WorkOrderScheduler.Schedule schedule = scheduler.compute(workOrder, start, workOrder.getPlannedQuantity());
        for (WorkOrderScheduler.Slot slot : schedule.slots()) {
            slot.operation().setPlannedStartAt(slot.start());
            slot.operation().setPlannedEndAt(slot.end());
        }
        workOrder.commitSchedule(schedule.calendarRevision(), schedule.timeZone(),
                now.truncatedTo(ChronoUnit.MICROS));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_CANCELLED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public WorkOrderResponse cancel(UUID workOrderId, WorkOrderCancelRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.WORK_ORDER_CANCEL, fingerprint).isPresent()) {
            return toResponse(findWorkOrder(workOrderId));
        }
        WorkOrder workOrder = findWorkOrder(workOrderId);
        // Fail before any state check (C9): a caller who forgot the reason should be told that,
        // not told the work order is in the wrong status.
        // G8b (FE Q33): present but shorter than 5 or longer than 2000 characters is a field error.
        String reason = ReasonRules.lengthChecked(request == null ? null : request.reason(), "reason");
        if (reason == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.APPROVAL_REASON_REQUIRED,
                    "Cancel reason is required");
        }
        WorkOrderVersions.ensure(workOrder, request.expectedWorkOrderVersion(), "expectedWorkOrderVersion");
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
        // G8a (FE R06): a request still waiting for a manager is activity too — cancelling under it would
        // leave a decision that could no longer be posted.
        if (materialIssueRepository.existsByWorkOrderWorkOrderIdAndStatus(workOrderId, MaterialIssueStatus.PENDING_APPROVAL)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Work order with a material issue pending approval cannot be cancelled; reject or cancel it first");
        }
        idempotencyRecords.record(key, WorkOrderCommand.WORK_ORDER_CANCEL, workOrderId, fingerprint);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        materialReservationService.cancelActiveReservations(workOrder);
        // G8a (FE R06/Q21): the planning quota comes back once. The sales order allocation needs no write:
        // coverage already ignores a cancelled work order (G6a), and the row stays as trace.
        planningLinkService.releaseWorkOrder(workOrderId, reason, now, auditorAware.getCurrentAuditor().orElse(null));
        workOrder.cancel(now, reason);
        return toResponse(workOrderRepository.saveAndFlush(workOrder));
    }

    /**
     * Reconciles and permanently locks a completed work order (P6). Any material still sitting in an
     * {@code ACTIVE} reservation (over-reserved component that was never issued) is released back to
     * available stock — the same reconciliation {@link #cancel} already performs — because once
     * {@code CLOSED} the work order can never be touched again to free it any other way.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_CLOSED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public WorkOrderResponse close(UUID workOrderId) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        if (!workOrder.canClose()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only completed work orders can be closed");
        }
        // G11c (D5, FE R32): a work order released with a cost baseline closes only through finalize-and-close, so
        // this path can never skip its checklist. Work orders released before G11 keep the P6 behaviour.
        if (costBaselineService.hasBaseline(workOrderId)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CLOSE_BLOCKED,
                    "This work order has a cost baseline; close it with POST /v1/work-orders/{id}/finalize-cost-and-close");
        }
        materialReservationService.cancelActiveReservations(workOrder);
        workOrder.close(Instant.now());
        return toResponse(workOrderRepository.saveAndFlush(workOrder));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_EXECUTE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_COMPONENT_ISSUED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
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
                        null,
                        request.quantity(),
                        request.reason(),
                        null))), idempotencyKey);
        // The response carries version (G8): flush so it is the version just written, not the one read.
        workOrderRepository.flush();
        return toResponse(findWorkOrder(workOrderId));
    }

    /**
     * Submits a production receipt for approval — it does NOT complete the work order.
     * Stock and completed quantity only change once the receipt is approved.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_EXECUTE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_SUBMITTED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public WorkOrderResponse completeOutput(UUID workOrderId,
                                            WorkOrderOutputCompletionRequest request,
                                            String idempotencyKey) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        productionReceiptService.postInternal(workOrderId, new ProductionReceiptPostRequest(
                workOrder.getOutputWarehouse().getWarehouseId(),
                request.lotId(),
                request.lotCode(),
                null,
                request.quantity(),
                request.reason(),
                null), idempotencyKey);
        workOrderRepository.flush();
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
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
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
                    .workCenterCode(operation.getWorkCenter().getCode())
                    .workCenter(operation.getWorkCenter())
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

    /**
     * G8a (D2): sets the warehouse of each listed component item on every line of that item. Validated as
     * a whole before anything is applied (C9): the item must be a component of this work order, listed
     * once, and the warehouse active in its plant. A component that already holds an {@code ACTIVE}
     * reservation in another warehouse is refused — release it first, or the line would carry stock held
     * outside the one warehouse it now says it uses.
     */
    private void applyComponentWarehouses(WorkOrder workOrder, List<ComponentWarehouseRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        Set<UUID> componentItems = new HashSet<>();
        workOrder.getComponentLines().forEach(line -> componentItems.add(line.getComponentItem().getItemId()));
        Map<String, String> errors = new LinkedHashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            UUID itemId = requests.get(i).itemId();
            if (!componentItems.contains(itemId)) {
                errors.put("componentWarehouses[" + i + "].itemId", "is not a component of this work order");
            } else if (!seen.add(itemId)) {
                errors.put("componentWarehouses[" + i + "].itemId", "is listed more than once");
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
        Map<UUID, Warehouse> warehouseByItem = new HashMap<>();
        for (ComponentWarehouseRequest request : requests) {
            warehouseByItem.put(request.itemId(), organizationLookupService.getActiveWarehouseInPlant(
                    request.warehouseId(), workOrder.getPlant().getPlantId()));
        }
        if (workOrder.getWorkOrderId() != null) {
            boolean reservedElsewhere = reservationRepository
                    .findByWorkOrderWorkOrderIdAndStatus(workOrder.getWorkOrderId(), MaterialReservationStatus.ACTIVE)
                    .stream()
                    .anyMatch(reservation -> {
                        Warehouse target = warehouseByItem.get(reservation.getItem().getItemId());
                        return target != null
                                && !target.getWarehouseId().equals(reservation.getWarehouse().getWarehouseId())
                                && reservation.remainingQuantity().signum() > 0;
                    });
            if (reservedElsewhere) {
                throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                        "A component already has active reservations in another warehouse; release them "
                                + "before changing its warehouse");
            }
        }
        for (WorkOrderComponentLine line : workOrder.getComponentLines()) {
            Warehouse warehouse = warehouseByItem.get(line.getComponentItem().getItemId());
            if (warehouse != null) {
                line.setWarehouse(warehouse);
            }
        }
    }

    /** FE Q7: what the planning quota and the sales order allocation were sized on cannot change. */
    private void ensureSourceFieldsUntouched(WorkOrder workOrder, WorkOrderUpdateRequest request) {
        if (!workOrder.isSourceLinked()) {
            return;
        }
        Map<String, String> locked = new LinkedHashMap<>();
        if (request.plannedQuantity() != null
                && request.plannedQuantity().compareTo(workOrder.getPlannedQuantity()) != 0) {
            locked.put("plannedQuantity", "comes from planning and cannot be changed");
        }
        if (request.outputWarehouseId() != null
                && !request.outputWarehouseId().equals(workOrder.getOutputWarehouse().getWarehouseId())) {
            locked.put("outputWarehouseId", "comes from planning and cannot be changed");
        }
        if (!locked.isEmpty()) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.SOURCE_LOCKED, locked);
        }
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
