package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.PlanningDemandLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderCancelRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftLineRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftUpdateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderUpdateRequest;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderPlanningDemandProjection;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.service.UomLookupService;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.SalesOrderCoverageLookupService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesOrderService {

    /**
     * Spec §2.1: only these order statuses contribute independent demand to a planning run.
     * {@code DRAFT} is not committed yet and {@code FULFILLED}/{@code CANCELLED} have nothing left
     * to produce.
     */
    static final Set<SalesOrderStatus> PLANNING_ELIGIBLE_STATUSES = Set.of(
            SalesOrderStatus.CONFIRMED,
            SalesOrderStatus.IN_PRODUCTION,
            SalesOrderStatus.PARTIALLY_FULFILLED);

    /** G6a: what can be sold through a draft — stock on hand, no BOM/routing required (FE §1.3). */
    static final Set<ItemType> SELLABLE_TYPES = Set.of(ItemType.FINISHED_GOOD, ItemType.WIP);

    private static final int MAX_ORDER_NUMBER_ATTEMPTS = 50;

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final CustomerRepository customerRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final UomLookupService uomLookupService;
    private final PlanningDemandService planningDemandService;
    private final SalesOrderStockReservationService stockReservationService;
    private final SalesOrderCoverageLookupService coverageLookupService;
    private final SalesOrderDetailAssembler detailAssembler;
    private final SalesOrderMapper mapper;
    private final DeliveryLookupService deliveryLookupService;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SALES_ORDER_MANAGE', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.SALES_ORDER_CREATED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public SalesOrderResponse create(SalesOrderCreateRequest request) {
        Company company = organizationLookupService.getActiveCompany(request.companyId());
        Plant plant = organizationLookupService.getActivePlant(request.plantId());
        ensurePlantBelongsToCompany(plant, company);
        String orderNo = normalizeCode(request.orderNo());
        if (salesOrderRepository.existsByCompanyCompanyIdAndOrderNo(company.getCompanyId(), orderNo)) {
            throw ExceptionFactory.alreadyExists(
                    ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Sales order number", orderNo);
        }

        SalesOrder order = SalesOrder.builder()
                .company(company)
                .plant(plant)
                .orderNo(orderNo)
                .customerName(request.customerName().trim())
                .orderDate(request.orderDate())
                .note(trimToNull(request.note()))
                .build();
        if (request.customerId() != null) {
            applyCustomer(order, resolveActiveCustomer(request.customerId(), company), null);
        }

        int lineNo = 1;
        for (SalesOrderLineRequest lineRequest : request.lines()) {
            order.getLines().add(buildLine(order, lineRequest, lineNo++));
        }
        return mapper.toResponse(salesOrderRepository.save(order), true);
    }

    /**
     * G6a (FE R01/R02, D1/D2): a DRAFT linked to an ACTIVE customer of the same company, with an
     * {@code orderNo} the server generates. Every line is validated before anything is written
     * (rule C9); the customer code/name are snapshotted (FE Q18).
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SALES_ORDER_MANAGE', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.SALES_ORDER_CREATED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public SalesOrderResponse createDraft(SalesOrderDraftCreateRequest request) {
        Company company = organizationLookupService.getActiveCompany(request.companyId());
        Plant plant = organizationLookupService.getActivePlant(request.plantId());
        ensurePlantBelongsToCompany(plant, company);
        Customer customer = resolveActiveCustomer(request.customerId(), company);
        List<Item> items = resolveDraftItems(request.orderDate(), request.lines(), company);

        SalesOrder order = SalesOrder.builder()
                .company(company)
                .plant(plant)
                .orderNo(nextOrderNo(company.getCompanyId()))
                .orderDate(request.orderDate())
                .note(trimToNull(request.note()))
                .build();
        applyCustomer(order, customer, request.customerReference());
        addDraftLines(order, request.lines(), items);
        SalesOrder saved = salesOrderRepository.saveAndFlush(order);
        return toDetail(saved);
    }

    /**
     * G6a full replacement of a DRAFT (FE R02). Same version guard and flush ordering as the legacy
     * {@link #update} ({@code B87}, {@code B112}).
     */
    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_MANAGE', #salesOrderId)")
    @Auditable(action = AuditAction.SALES_ORDER_UPDATED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public SalesOrderResponse updateDraft(UUID salesOrderId, SalesOrderDraftUpdateRequest request) {
        SalesOrder order = findOrder(salesOrderId);
        if (!order.isDraft()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Only DRAFT sales orders can be updated");
        }
        ensureVersion(order, request.expectedVersion());
        Customer customer = resolveActiveCustomer(request.customerId(), order.getCompany());
        List<Item> items = resolveDraftItems(request.orderDate(), request.lines(), order.getCompany());

        applyCustomer(order, customer, request.customerReference());
        order.setOrderDate(request.orderDate());
        order.setNote(trimToNull(request.note()));
        if (pricesChange(order.getLines(), request.lines())) {
            order.bumpPricingRevision();
        }
        order.getLines().clear();
        order.setUpdatedAt(Instant.now());
        salesOrderRepository.flush();
        addDraftLines(order, request.lines(), items);
        return toDetail(salesOrderRepository.saveAndFlush(order));
    }

    /**
     * Full-replace update, {@code DRAFT} only (spec gap doc §4.3). {@code request.lines() == null}
     * keeps the existing lines; a non-null list drops every existing line
     * ({@code orphanRemoval = true} on {@link SalesOrder#getLines()}) and rebuilds {@code lineNo}
     * 1..N via the same {@link #buildLine} used by {@code create}.
     *
     * <p>{@code expectedVersion} is compared by hand against {@link SalesOrder#getVersion()} —
     * unlike a real concurrent write, there is nothing "stale" for JPA to detect on a freshly loaded
     * entity, so the optimistic-lock check has to be explicit here (rule {@code C9}: fail before
     * mutating anything).
     */
    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_MANAGE', #salesOrderId)")
    @Auditable(action = AuditAction.SALES_ORDER_UPDATED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public SalesOrderResponse update(UUID salesOrderId, SalesOrderUpdateRequest request) {
        SalesOrder order = findOrder(salesOrderId);
        if (!order.isDraft()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Only DRAFT sales orders can be updated");
        }
        if (!request.expectedVersion().equals(order.getVersion())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Sales order was modified by another request");
        }
        LocalDate effectiveOrderDate = request.orderDate() != null ? request.orderDate() : order.getOrderDate();
        ensureDueDatesRespectOrderDate(effectiveOrderDate, request.lines(), order.getLines());

        if (request.customerName() != null) {
            order.setCustomerName(request.customerName().trim());
        }
        if (request.orderDate() != null) {
            order.setOrderDate(request.orderDate());
        }
        if (request.note() != null) {
            order.setNote(trimToNull(request.note()));
        }
        if (request.lines() != null) {
            replaceLines(order, request.lines());
        }
        // saveAndFlush (not save): the response now carries `version` (FE contract fix, 2026-08-06)
        // for the client's *next* expectedVersion. A plain save() only queues the UPDATE — Hibernate
        // doesn't bump the in-memory @Version field until that UPDATE actually flushes, which
        // otherwise happens at commit, after mapper.toResponse() already ran. Without the flush here
        // the response would report the pre-update version while the DB row is already one ahead,
        // so a client trusting it would retry with a version that's already stale.
        return mapper.toResponse(salesOrderRepository.saveAndFlush(order), true);
    }

    /**
     * Confirms the order and, in the same transaction, creates one {@code SALES_ORDER} planning
     * demand per line — this is the point where a commercial commitment becomes something MRP can
     * plan against (spec §1).
     *
     * <p>G6a: {@code expectedVersion} is optional ({@code null} = legacy call, no check). The customer
     * and every item are re-checked ACTIVE (FE R02).
     *
     * <p>G10a (D4): an order without a master customer cannot be confirmed — invoices and payments are
     * bound to the customer master (FE Q4), and a free-text name cannot be locked. {@code 422} on the
     * {@code customerId} field; the client links one through {@code PATCH /{id}/draft} first.
     */
    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_MANAGE', #salesOrderId)")
    @Auditable(action = AuditAction.SALES_ORDER_CONFIRMED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public SalesOrderResponse confirm(UUID salesOrderId, Long expectedVersion) {
        SalesOrder order = findOrder(salesOrderId);
        if (!order.isDraft()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Only DRAFT sales orders can be confirmed");
        }
        if (expectedVersion != null) {
            ensureVersion(order, expectedVersion);
        }
        if (order.getCustomerId() == null) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    Map.of("customerId", "a customer from the customer master is required to confirm"));
        }
        resolveActiveCustomer(order.getCustomerId(), order.getCompany());
        for (SalesOrderLine line : order.getLines()) {
            itemLookupService.getActiveItem(line.getItem().getItemId());
        }
        order.confirm();
        for (SalesOrderLine line : order.getLines()) {
            planningDemandService.createFromSalesOrderLine(
                    order.getCompany(),
                    order.getPlant(),
                    line.getItem(),
                    line.getOrderedQuantity(),
                    line.getDueDate(),
                    line.getSalesOrderLineId());
        }
        // saveAndFlush: see update()'s comment — the response's `version` must reflect this write.
        return toDetail(salesOrderRepository.saveAndFlush(order));
    }

    /**
     * Cancellable up to {@code CONFIRMED} only: once production has started
     * ({@code IN_PRODUCTION} onwards) there are work orders and possibly issued material behind the
     * order, and unwinding those is not a sales-side decision. Cancelling a confirmed order also
     * cancels the demand it generated, so MRP stops planning for it.
     *
     * <p>G10b (FE R26/Q37): refused while a DRAFT or POSTED delivery stands behind the order — a posted
     * delivery is irreversible, and a draft must be cancelled by its author first.
     *
     * <p>G6a: also releases every stock hold of <em>this</em> order in the same transaction (FE R06).
     * The work-order guard is the status rule above: allocating a work order moves the order to
     * {@code IN_PRODUCTION}, so an order with a work order behind it can never get here (FE Q24 —
     * refuse, never cascade). {@code request} is optional; when sent, its reason is stored.
     */
    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_MANAGE', #salesOrderId)")
    @Auditable(action = AuditAction.SALES_ORDER_CANCELLED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public SalesOrderResponse cancel(UUID salesOrderId, SalesOrderCancelRequest request) {
        SalesOrder order = findOrder(salesOrderId);
        if (!order.isDraft() && !order.isConfirmed()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Only DRAFT or CONFIRMED sales orders can be cancelled");
        }
        if (request != null && request.expectedVersion() != null) {
            ensureVersion(order, request.expectedVersion());
        }
        if (deliveryLookupService.hasOpenDeliveries(order.getSalesOrderId())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "A draft or posted delivery stands behind this sales order; cancel the draft first");
        }
        if (order.isConfirmed()) {
            planningDemandService.cancelOpenDemandsForSalesOrderLines(
                    order.getLines().stream().map(SalesOrderLine::getSalesOrderLineId).toList());
            stockReservationService.cancelActiveForSalesOrder(order.getSalesOrderId());
        }
        if (request != null) {
            order.setCancelReason(request.reason().trim());
        }
        order.cancel();
        // saveAndFlush: see update()'s comment — the response's `version` must reflect this write.
        return toDetail(salesOrderRepository.saveAndFlush(order));
    }

    /**
     * G6a list (FE R03). Every filter is optional; the due-date range applies to the order's
     * earliest line due date (FE Q3). Two queries per page: the orders, then one batch line summary.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SALES_ORDER_READ', 'PLANT', #plantId)")
    public PageResult<SalesOrderResponse> list(UUID companyId,
                                               UUID plantId,
                                               SalesOrderStatus status,
                                               String search,
                                               LocalDate orderDateFrom,
                                               LocalDate orderDateTo,
                                               LocalDate dueDateFrom,
                                               LocalDate dueDateTo,
                                               Pageable pageable) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        ensurePlantBelongsToCompany(plant, company);
        Page<SalesOrder> page = salesOrderRepository.searchExtended(companyId, plantId, status,
                trimToNull(search), orderDateFrom, orderDateTo, dueDateFrom, dueDateTo, pageable);
        List<UUID> orderIds = page.getContent().stream().map(SalesOrder::getSalesOrderId).toList();
        Map<UUID, SalesOrderLineRepository.OrderLineSummaryProjection> summaries = orderIds.isEmpty()
                ? Map.of()
                : salesOrderLineRepository.summarizeByOrderIds(orderIds).stream()
                        .collect(Collectors.toMap(
                                SalesOrderLineRepository.OrderLineSummaryProjection::getSalesOrderId,
                                Function.identity()));
        return PageResult.from(page.map(order -> mapper.toListResponse(order, summaries.get(order.getSalesOrderId()))));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_READ', #salesOrderId)")
    public SalesOrderResponse get(UUID salesOrderId) {
        return toDetail(findOrder(salesOrderId));
    }

    /**
     * Demand lines a planning run may select (spec §2.1/§2.2). Guarded by the planning permission,
     * not a sales one: this is the planner's screen and spec §2.2 assigns it {@code PLANNING_RUN}
     * — {@code PERM_MRP_RUN} in this codebase.
     *
     * <p>Batch queries only, never one per line (rule {@code C14}): the eligibility JPQL, the
     * {@code planningDemandId} lookup (debt #21), and — since G6a — held stock and work-order
     * coverage. {@code uncoveredOnly} drops lines with nothing left to plan (FE R07); the default keeps
     * the legacy list unchanged.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MRP_RUN', 'PLANT', #plantId)")
    public List<PlanningDemandLineResponse> planningDemands(UUID plantId, LocalDate horizonEnd, boolean uncoveredOnly) {
        List<SalesOrderPlanningDemandProjection> eligible = salesOrderLineRepository
                .findEligiblePlanningDemands(plantId, horizonEnd, PLANNING_ELIGIBLE_STATUSES);
        List<UUID> lineIds = eligible.stream().map(SalesOrderPlanningDemandProjection::getSalesOrderLineId).toList();
        Map<UUID, UUID> demandIdByLineId = planningDemandService.findOpenDemandIdsBySalesOrderLineIds(lineIds);
        Map<UUID, BigDecimal> held = stockReservationService.sumActiveHeldBySalesOrderLineIds(lineIds);
        Map<UUID, BigDecimal> coverage = coverageLookupService.sumOpenCoverageBySalesOrderLineIds(lineIds);
        Map<UUID, BigDecimal> delivered = deliveryLookupService.sumDeliveredBySalesOrderLineIds(lineIds);
        Map<UUID, BigDecimal> fulfilled = salesOrderLineRepository.findAllById(lineIds).stream()
                .collect(Collectors.toMap(SalesOrderLine::getSalesOrderLineId, SalesOrderLine::getFulfilledQuantity));
        return eligible.stream()
                .map(projection -> mapper.toResponse(
                        projection,
                        demandIdByLineId.get(projection.getSalesOrderLineId()),
                        held.getOrDefault(projection.getSalesOrderLineId(), BigDecimal.ZERO),
                        coverage.getOrDefault(projection.getSalesOrderLineId(), BigDecimal.ZERO),
                        deliveredBeyondQc(delivered.get(projection.getSalesOrderLineId()),
                                fulfilled.get(projection.getSalesOrderLineId()))))
                .filter(line -> !uncoveredOnly || line.uncoveredQuantity().signum() > 0)
                .toList();
    }

    private SalesOrderResponse toDetail(SalesOrder order) {
        return detailAssembler.toDetail(order);
    }

    /** G10b (D8): the part of the delivered quantity QC acceptance does not already account for. */
    private static BigDecimal deliveredBeyondQc(BigDecimal delivered, BigDecimal fulfilled) {
        if (delivered == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal beyond = delivered.subtract(fulfilled == null ? BigDecimal.ZERO : fulfilled);
        return beyond.signum() > 0 ? beyond : BigDecimal.ZERO;
    }

    private void ensureVersion(SalesOrder order, Long expectedVersion) {
        if (!Objects.equals(expectedVersion, order.getVersion())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Sales order was modified by another request");
        }
    }

    /**
     * Drops every existing line and rebuilds {@code lineNo} 1..N ({@code B87}).
     *
     * <p>🔴 The {@code flush()} in the middle is load-bearing, not a tidy-up. In a single flush
     * Hibernate executes the child {@code INSERT}s <em>before</em> the orphan-removal
     * {@code DELETE}s, so a replacement that reuses {@code lineNo} 1..N — which every replacement
     * does — collides with {@code uk_sales_order_lines_order_line_no} and the whole {@code PATCH}
     * fails with {@code RESOURCE_ALREADY_EXISTS} (FE defect report 2026-08-10). Flushing the
     * deletes first makes the order deterministic instead of relying on Hibernate's action
     * ordering. Both statements stay inside the caller's transaction, so an invalid replacement
     * line still rolls the deletes back with everything else.
     *
     * <p>🔴 The {@code setUpdatedAt} is load-bearing too, for a second reason found in the same
     * smoke test: {@code lines} is an <em>inverse</em> ({@code mappedBy}) collection, so replacing it
     * does not dirty the header row and Hibernate issues no {@code UPDATE sales_orders} — leaving
     * {@code version} unchanged on a lines-only {@code PATCH}. That breaks the very guarantee
     * {@code expectedVersion} exists for: two concurrent replacements both read version <i>n</i>,
     * both pass the check, and the second silently wins. Touching an audited header field makes the
     * aggregate dirty so exactly one {@code UPDATE} (hence exactly one version bump) is issued for
     * every accepted replacement. The value is overwritten by {@code @LastModifiedDate} at flush,
     * which is the same instant it would otherwise get — the point is the dirtiness, not the value.
     */
    private void replaceLines(SalesOrder order, List<SalesOrderLineRequest> lineRequests) {
        order.getLines().clear();
        order.setUpdatedAt(Instant.now());
        salesOrderRepository.flush();
        int lineNo = 1;
        for (SalesOrderLineRequest lineRequest : lineRequests) {
            order.getLines().add(buildLine(order, lineRequest, lineNo++));
        }
    }

    private SalesOrderLine buildLine(SalesOrder order, SalesOrderLineRequest request, int lineNo) {
        Item item = itemLookupService.getActiveItem(request.itemId());
        ensureItemBelongsToCompany(item, order.getCompany());
        if (request.dueDate().isBefore(order.getOrderDate())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Line due date cannot be before the order date");
        }
        return SalesOrderLine.builder()
                .salesOrder(order)
                .item(item)
                .lineNo(lineNo)
                .orderedQuantity(requirePositive(request.orderedQuantity()))
                .fulfilledQuantity(BigDecimal.ZERO)
                .dueDate(request.dueDate())
                .build();
    }

    /**
     * Validates every line of a G6a draft before anything is written: due date first (a field error a
     * form can bind, FE A03), then each item — ACTIVE, same company, sellable type, unit ACTIVE in the
     * UOM master when it is there. Returns the items in line order.
     */
    private List<Item> resolveDraftItems(LocalDate orderDate, List<SalesOrderDraftLineRequest> lines, Company company) {
        Map<String, String> dateErrors = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).dueDate().isBefore(orderDate)) {
                dateErrors.put("lines[" + i + "].dueDate", "must not be before orderDate");
            }
        }
        if (!dateErrors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, dateErrors);
        }
        List<Item> items = lines.stream().map(line -> itemLookupService.getActiveItem(line.itemId())).toList();
        Map<String, Uom> uoms = uomLookupService.findByCode(
                items.stream().map(Item::getUnit).filter(Objects::nonNull).distinct().toList());
        for (Item item : items) {
            ensureItemBelongsToCompany(item, company);
            if (!SELLABLE_TYPES.contains(item.getType())) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Only finished goods and WIP items can be sold: " + item.getCode());
            }
            Uom uom = uoms.get(item.getUnit());
            if (uom != null && !uom.isActive()) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                        "Unit of measure is inactive: " + item.getUnit());
            }
        }
        return items;
    }

    private void addDraftLines(SalesOrder order, List<SalesOrderDraftLineRequest> lines, List<Item> items) {
        for (int i = 0; i < lines.size(); i++) {
            SalesOrderDraftLineRequest line = lines.get(i);
            order.getLines().add(SalesOrderLine.builder()
                    .salesOrder(order)
                    .item(items.get(i))
                    .lineNo(i + 1)
                    .orderedQuantity(line.orderedQuantity())
                    .fulfilledQuantity(BigDecimal.ZERO)
                    .dueDate(line.dueDate())
                    .unitPrice(line.unitPrice())
                    .build());
        }
    }

    /**
     * G10a: whether a draft replacement changes any line price, compared line by line in {@code lineNo} order (line
     * ids are reissued on every save, so position is the only identity a draft has). Moves {@code pricingVersion}.
     */
    static boolean pricesChange(List<SalesOrderLine> existing, List<SalesOrderDraftLineRequest> replacement) {
        List<BigDecimal> before = existing.stream()
                .sorted(java.util.Comparator.comparing(SalesOrderLine::getLineNo))
                .map(SalesOrderLine::getUnitPrice)
                .toList();
        List<BigDecimal> after = replacement.stream().map(SalesOrderDraftLineRequest::unitPrice).toList();
        if (before.size() != after.size()) {
            return before.stream().anyMatch(Objects::nonNull) || after.stream().anyMatch(Objects::nonNull);
        }
        for (int i = 0; i < before.size(); i++) {
            BigDecimal b = before.get(i);
            BigDecimal a = after.get(i);
            if (b == null ? a != null : (a == null || b.compareTo(a) != 0)) {
                return true;
            }
        }
        return false;
    }

    private Customer resolveActiveCustomer(UUID customerId, Company company) {
        Customer customer = customerRepository.findWithCompanyByCustomerId(customerId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Customer", customerId));
        if (!customer.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Customer must belong to the order's company");
        }
        if (!customer.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive customer cannot be used: " + customer.getCode());
        }
        return customer;
    }

    private void applyCustomer(SalesOrder order, Customer customer, String customerReference) {
        order.setCustomerId(customer.getCustomerId());
        order.setCustomerCode(customer.getCode());
        order.setCustomerName(customer.getName());
        order.setCustomerReference(trimToNull(customerReference));
    }

    /**
     * {@code SO-000001}… from {@code sales_order_number_seq}. A value an older, client-chosen
     * {@code orderNo} already took in this company is skipped, never reused.
     */
    private String nextOrderNo(UUID companyId) {
        for (int attempt = 0; attempt < MAX_ORDER_NUMBER_ATTEMPTS; attempt++) {
            String candidate = String.format("SO-%06d", salesOrderRepository.nextOrderNumber());
            if (!salesOrderRepository.existsByCompanyCompanyIdAndOrderNo(companyId, candidate)) {
                return candidate;
            }
        }
        throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                "Could not allocate a free sales order number; retry");
    }

    /**
     * If {@code newLines} is non-null (a full line replace), validate against the request's due
     * dates — the entity's own lines are about to be dropped. Otherwise the existing lines survive
     * unchanged, so an order-date-only edit can still make them invalid and must be checked too.
     */
    private void ensureDueDatesRespectOrderDate(LocalDate orderDate,
                                                 List<SalesOrderLineRequest> newLines,
                                                 List<SalesOrderLine> existingLines) {
        List<LocalDate> dueDates = newLines != null
                ? newLines.stream().map(SalesOrderLineRequest::dueDate).toList()
                : existingLines.stream().map(SalesOrderLine::getDueDate).toList();
        for (LocalDate dueDate : dueDates) {
            if (dueDate.isBefore(orderDate)) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Line due date cannot be before the order date");
            }
        }
    }

    private SalesOrder findOrder(UUID salesOrderId) {
        return salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales order", salesOrderId));
    }

    private void ensurePlantBelongsToCompany(Plant plant, Company company) {
        if (!plant.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Plant must belong to the selected company");
        }
    }

    private void ensureItemBelongsToCompany(Item item, Company company) {
        if (!item.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Item must belong to the selected company");
        }
    }

    private BigDecimal requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    "Ordered quantity must be greater than zero");
        }
        return quantity;
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "Sales order number is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
