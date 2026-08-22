package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.PlanningDemandLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderUpdateRequest;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderPlanningDemandProjection;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final PlanningDemandService planningDemandService;
    private final SalesOrderMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SALES_ORDER_MANAGE', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.SALES_ORDER_CREATED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()")
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

        int lineNo = 1;
        for (SalesOrderLineRequest lineRequest : request.lines()) {
            order.getLines().add(buildLine(order, lineRequest, lineNo++));
        }
        return mapper.toResponse(salesOrderRepository.save(order), true);
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
    @Auditable(action = AuditAction.SALES_ORDER_UPDATED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()")
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
     */
    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_MANAGE', #salesOrderId)")
    @Auditable(action = AuditAction.SALES_ORDER_CONFIRMED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()")
    public SalesOrderResponse confirm(UUID salesOrderId) {
        SalesOrder order = findOrder(salesOrderId);
        if (!order.isDraft()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Only DRAFT sales orders can be confirmed");
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
        return mapper.toResponse(salesOrderRepository.saveAndFlush(order), true);
    }

    /**
     * Cancellable up to {@code CONFIRMED} only: once production has started
     * ({@code IN_PRODUCTION} onwards) there are work orders and possibly issued material behind the
     * order, and unwinding those is not a sales-side decision. Cancelling a confirmed order also
     * cancels the demand it generated, so MRP stops planning for it.
     */
    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_MANAGE', #salesOrderId)")
    @Auditable(action = AuditAction.SALES_ORDER_CANCELLED, entityType = "SalesOrder", entityIdExpression = "salesOrderId.toString()")
    public SalesOrderResponse cancel(UUID salesOrderId) {
        SalesOrder order = findOrder(salesOrderId);
        if (!order.isDraft() && !order.isConfirmed()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Only DRAFT or CONFIRMED sales orders can be cancelled");
        }
        if (order.isConfirmed()) {
            planningDemandService.cancelOpenDemandsForSalesOrderLines(
                    order.getLines().stream().map(SalesOrderLine::getSalesOrderLineId).toList());
        }
        order.cancel();
        // saveAndFlush: see update()'s comment — the response's `version` must reflect this write.
        return mapper.toResponse(salesOrderRepository.saveAndFlush(order), true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SALES_ORDER_READ', 'PLANT', #plantId)")
    public PageResult<SalesOrderResponse> list(UUID companyId,
                                               UUID plantId,
                                               SalesOrderStatus status,
                                               Pageable pageable) {
        return list(companyId, plantId, status, null, pageable);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SALES_ORDER_READ', 'PLANT', #plantId)")
    public PageResult<SalesOrderResponse> list(UUID companyId,
                                               UUID plantId,
                                               SalesOrderStatus status,
                                               String search,
                                               Pageable pageable) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        ensurePlantBelongsToCompany(plant, company);
        String normalizedSearch = trimToNull(search);
        var page = normalizedSearch == null
                ? salesOrderRepository.search(companyId, plantId, status, pageable)
                : salesOrderRepository.searchWithText(companyId, plantId, status, normalizedSearch, pageable);
        return PageResult.from(page
                .map(order -> mapper.toResponse(order, false)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_ORDER_READ', #salesOrderId)")
    public SalesOrderResponse get(UUID salesOrderId) {
        return mapper.toResponse(findOrder(salesOrderId), true);
    }

    /**
     * Demand lines a planning run may select (spec §2.1/§2.2). Guarded by the planning permission,
     * not a sales one: this is the planner's screen and spec §2.2 assigns it {@code PLANNING_RUN}
     * — {@code PERM_MRP_RUN} in this codebase.
     *
     * <p>Two queries, never one per line (rule {@code C14}): the eligibility JPQL, then a single batch
     * lookup that attaches the {@code planningDemandId} the run needs (debt #21).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MRP_RUN', 'PLANT', #plantId)")
    public List<PlanningDemandLineResponse> planningDemands(UUID plantId, LocalDate horizonEnd) {
        List<SalesOrderPlanningDemandProjection> eligible = salesOrderLineRepository
                .findEligiblePlanningDemands(plantId, horizonEnd, PLANNING_ELIGIBLE_STATUSES);
        Map<UUID, UUID> demandIdByLineId = planningDemandService.findOpenDemandIdsBySalesOrderLineIds(
                eligible.stream().map(SalesOrderPlanningDemandProjection::getSalesOrderLineId).toList());
        return eligible.stream()
                .map(projection -> mapper.toResponse(
                        projection, demandIdByLineId.get(projection.getSalesOrderLineId())))
                .toList();
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
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Plant must belong to the selected company");
        }
    }

    private void ensureItemBelongsToCompany(Item item, Company company) {
        if (!item.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
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
