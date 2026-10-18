package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.dto.PurchaseOrderPlanningSource;
import com.erp.manufacturing.module.planning.service.PurchaseOrderPlanningLinkService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseRequisitionRepository;
import com.erp.manufacturing.module.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    /**
     * G7a (FE Q6): what a purchase order created or edited through the new draft endpoints may buy.
     * {@code FINISHED_GOOD} and {@code SERVICE} are not bought here, and serial-tracked items cannot be
     * received through a goods receipt yet (purchasing debt, P5).
     */
    static final Set<ItemType> PURCHASABLE_TYPES = EnumSet.of(ItemType.RAW_MATERIAL, ItemType.CONSUMABLE, ItemType.WIP);

    /** G7a (FE Q5): purchase orders are priced in VND only, no tax/fee/FX. */
    static final String CURRENCY_VND = "VND";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final SupplierService supplierService;
    private final PurchasingMapper mapper;
    private final PurchaseOrderPlanningLinkService planningLinkService;
    private final PurchasingIdempotency idempotencyRecords;
    private final UserLookupService userLookupService;
    private final AuditorAware<UUID> auditorAware;
    private final GoodsReceiptRepository goodsReceiptRepository;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.PURCHASE_ORDER_CREATED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse create(PurchaseOrderCreateRequest request) {
        PurchaseOrder order = buildOrder(
                request.companyId(),
                request.plantId(),
                request.warehouseId(),
                supplierService.findActiveSupplier(request.supplierId()),
                normalizeCode(request.purchaseOrderNo(), "Purchase order number"),
                request.orderDate(),
                request.expectedDate(),
                request.sourceRequisitionId() == null ? null : findRequisition(request.sourceRequisitionId()),
                trimToNull(request.note()));
        for (PurchaseOrderLineRequest lineRequest : request.lines()) {
            order.getLines().add(buildManualLine(order, lineRequest));
        }
        return mapper.toResponse(purchaseOrderRepository.save(order), true);
    }

    /**
     * {@code POST /v1/purchase-orders/drafts} (G7a, D2): a DRAFT numbered by the server
     * ({@code PO-000001}…, from {@code purchase_order_number_seq}). Line dates are checked before any
     * lookup; then supplier, warehouse and items as on {@link #create}, plus the purchasable item
     * types. Lines are priced in VND.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.PURCHASE_ORDER_CREATED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse createDraft(PurchaseOrderDraftCreateRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        Optional<UUID> replayed = idempotencyRecords.replay(key, PurchasingCommand.PURCHASE_ORDER_CREATE_DRAFT, request);
        if (replayed.isPresent()) {
            return detail(findOrder(replayed.get()));
        }
        Map<String, String> errors = validateDraftLines(request.orderDate(), request.lines());
        for (int i = 0; i < request.lines().size(); i++) {
            if (request.lines().get(i).id() != null) {
                errors.put("lines[" + i + "].id", "must be omitted when creating a purchase order");
            }
        }
        failOnErrors(errors);

        PurchaseOrder order = buildOrder(
                request.companyId(),
                request.plantId(),
                request.warehouseId(),
                supplierService.findActiveSupplier(request.supplierId()),
                nextDraftOrderNo(request.companyId()),
                request.orderDate(),
                earliestExpectedDate(request.lines()),
                null,
                trimToNull(request.notes()));
        order.setReference(trimToNull(request.reference()));
        Map<String, String> itemErrors = new LinkedHashMap<>();
        for (int i = 0; i < request.lines().size(); i++) {
            PurchaseOrderDraftLineRequest lineRequest = request.lines().get(i);
            Item item = purchasableItem(lineRequest.itemId(), order.getCompany(), i, itemErrors);
            order.getLines().add(applyDraftLine(PurchaseOrderLine.builder()
                    .purchaseOrder(order)
                    .receivedQuantity(BigDecimal.ZERO)
                    .build(), item, lineRequest));
        }
        failOnItemErrors(itemErrors);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        idempotencyRecords.record(key, PurchasingCommand.PURCHASE_ORDER_CREATE_DRAFT, saved.getPurchaseOrderId(), request);
        return detail(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PURCHASE_ORDER_READ', 'PLANT', #plantId)")
    public PageResult<PurchaseOrderResponse> list(UUID companyId,
                                                  UUID plantId,
                                                  UUID warehouseId,
                                                  UUID supplierId,
                                                  PurchaseOrderStatus status,
                                                  String search,
                                                  LocalDate orderDateFrom,
                                                  LocalDate orderDateTo,
                                                  Pageable pageable) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        ensurePlantBelongsToCompany(plant, company);
        if (warehouseId != null) {
            ensureWarehouseBelongsToPlant(organizationLookupService.getActiveWarehouse(warehouseId), plant);
        }
        if (orderDateFrom != null && orderDateTo != null && orderDateFrom.isAfter(orderDateTo)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("orderDateFrom", "must not be after orderDateTo"));
        }
        Page<PurchaseOrder> page = purchaseOrderRepository.search(companyId, plantId, warehouseId, supplierId, status,
                trimToNull(search), orderDateFrom, orderDateTo, pageable);
        Set<UUID> locked = planningLinkService.findSourceLockedOrderIds(
                page.getContent().stream().map(PurchaseOrder::getPurchaseOrderId).toList());
        return PageResult.from(page.map(order -> mapper.toResponse(order, false, Map.of(),
                locked.contains(order.getPurchaseOrderId()), List.of())));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_READ', #purchaseOrderId)")
    public PurchaseOrderResponse get(UUID purchaseOrderId) {
        return detail(findOrder(purchaseOrderId));
    }

    /**
     * {@code PATCH /v1/purchase-orders/{id}} (G7a): DRAFT only, full replacement of the lines with
     * stable line ids (FE Q3). An order that still holds planning quota keeps its warehouse, items,
     * quantities and line set — {@code SOURCE_LOCKED} — while supplier, prices, dates, reference and
     * notes stay editable (FE Q7). The version always moves: the lines are an inverse collection, so
     * without touching the header a lines-only edit would leave {@code version} where it was (B112).
     */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #purchaseOrderId)")
    @Auditable(action = AuditAction.PURCHASE_ORDER_UPDATED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse updateDraft(UUID purchaseOrderId, PurchaseOrderUpdateRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        PurchasingIdempotency.Fingerprint fingerprint = new PurchasingIdempotency.Fingerprint(purchaseOrderId, request);
        if (idempotencyRecords.replay(key, PurchasingCommand.PURCHASE_ORDER_UPDATE, fingerprint).isPresent()) {
            return detail(findOrder(purchaseOrderId));
        }
        PurchaseOrder order = findOrder(purchaseOrderId);
        ensureVersion(order, request.expectedVersion());
        ensureDraft(order, "Only DRAFT purchase orders can be edited");

        Map<UUID, PurchaseOrderLine> existing = order.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getPurchaseOrderLineId, line -> line));
        Map<String, String> errors = validateDraftLines(request.orderDate(), request.lines());
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            UUID lineId = request.lines().get(i).id();
            if (lineId == null) {
                continue;
            }
            if (!seen.add(lineId)) {
                errors.put("lines[" + i + "].id", "is listed more than once");
            } else if (!existing.containsKey(lineId)) {
                errors.put("lines[" + i + "].id", "is not a line of this purchase order");
            }
        }
        failOnErrors(errors);
        if (planningLinkService.findSourceLockedOrderIds(List.of(purchaseOrderId)).contains(purchaseOrderId)) {
            ensureSourceFieldsUnchanged(order, request, existing);
        }
        idempotencyRecords.record(key, PurchasingCommand.PURCHASE_ORDER_UPDATE, purchaseOrderId, fingerprint);

        Supplier supplier = supplierService.findActiveSupplier(request.supplierId());
        ensureSupplierBelongsToCompany(supplier, order.getCompany());
        Warehouse warehouse = organizationLookupService.getActiveWarehouse(request.warehouseId());
        ensureWarehouseBelongsToPlant(warehouse, order.getPlant());

        Map<String, String> itemErrors = new LinkedHashMap<>();
        List<PurchaseOrderLine> kept = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            PurchaseOrderDraftLineRequest lineRequest = request.lines().get(i);
            PurchaseOrderLine line = lineRequest.id() == null ? null : existing.get(lineRequest.id());
            boolean itemChanged = line == null || !line.getItem().getItemId().equals(lineRequest.itemId());
            // An unchanged item is not re-checked against the G7a type rule: a line drafted by an older
            // path (requisition, planning) stays editable for its price and date.
            Item item = itemChanged
                    ? purchasableItem(lineRequest.itemId(), order.getCompany(), i, itemErrors)
                    : line.getItem();
            if (line == null) {
                line = PurchaseOrderLine.builder().purchaseOrder(order).receivedQuantity(BigDecimal.ZERO).build();
                order.getLines().add(line);
            }
            kept.add(applyDraftLine(line, item, lineRequest));
        }
        failOnItemErrors(itemErrors);
        order.getLines().removeIf(line -> kept.stream().noneMatch(k -> k == line));

        order.setSupplier(supplier);
        order.setWarehouse(warehouse);
        order.setOrderDate(request.orderDate());
        order.setExpectedDate(earliestExpectedDate(request.lines()));
        if (request.reference() != null) {
            order.setReference(trimToNull(request.reference()));
        }
        if (request.notes() != null) {
            order.setNote(trimToNull(request.notes()));
        }
        order.setUpdatedAt(Instant.now());
        return detail(purchaseOrderRepository.saveAndFlush(order));
    }

    /**
     * {@code POST /{id}/confirm} (G7a): DRAFT → CONFIRMED after re-checking company, plant, warehouse,
     * supplier and items are still ACTIVE and in scope, and that every line has a price — explicit
     * {@code 0} counts, a missing price does not (FE Q4). Confirmed orders count as open supply for MRP.
     */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #purchaseOrderId)")
    @Auditable(action = AuditAction.PURCHASE_ORDER_CONFIRMED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse confirm(UUID purchaseOrderId, PurchaseOrderVersionRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        PurchasingIdempotency.Fingerprint fingerprint = new PurchasingIdempotency.Fingerprint(purchaseOrderId, request);
        if (idempotencyRecords.replay(key, PurchasingCommand.PURCHASE_ORDER_CONFIRM, fingerprint).isPresent()) {
            return detail(findOrder(purchaseOrderId));
        }
        PurchaseOrder order = findOrder(purchaseOrderId);
        ensureVersion(order, request.expectedVersion());
        ensureDraft(order, "Only DRAFT purchase orders can be confirmed");
        idempotencyRecords.record(key, PurchasingCommand.PURCHASE_ORDER_CONFIRM, purchaseOrderId, fingerprint);

        Company company = organizationLookupService.getActiveCompany(order.getCompany().getCompanyId());
        organizationLookupService.getActivePlant(order.getPlant().getPlantId());
        organizationLookupService.getActiveWarehouse(order.getWarehouse().getWarehouseId());
        ensureSupplierBelongsToCompany(supplierService.findActiveSupplier(order.getSupplier().getSupplierId()), company);
        List<PurchaseOrderLine> lines = sortedLines(order);
        Map<String, String> priceErrors = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            PurchaseOrderLine line = lines.get(i);
            ensureItemBelongsToCompany(itemLookupService.getActiveItem(line.getItem().getItemId()), company);
            if (line.getUnitPrice() == null) {
                priceErrors.put("lines[" + i + "].unitPrice", "is required to confirm");
            }
        }
        if (!priceErrors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, priceErrors);
        }
        order.confirm(now(), currentActor());
        return detail(purchaseOrderRepository.saveAndFlush(order));
    }

    /** Legacy {@code POST /{id}/send}: DRAFT → CONFIRMED with no body, no version and no price check. */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #purchaseOrderId)")
    @Auditable(action = AuditAction.PURCHASE_ORDER_SENT, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse send(UUID purchaseOrderId) {
        PurchaseOrder order = findOrder(purchaseOrderId);
        ensureDraft(order, "Only DRAFT purchase orders can be sent");
        order.confirm(now(), currentActor());
        return detail(purchaseOrderRepository.saveAndFlush(order));
    }

    /**
     * {@code POST /{id}/cancel}. Without a body: the legacy rule, DRAFT only. With a body (G7a): a DRAFT
     * or CONFIRMED order nothing was received on, with a version check and a reason. Either way the
     * planning quota the order held goes back to its runs once (FE Q12).
     */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #purchaseOrderId)")
    @Auditable(action = AuditAction.PURCHASE_ORDER_CANCELLED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse cancel(UUID purchaseOrderId, PurchaseOrderReasonRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        PurchasingIdempotency.Fingerprint fingerprint = new PurchasingIdempotency.Fingerprint(purchaseOrderId, request);
        if (idempotencyRecords.replay(key, PurchasingCommand.PURCHASE_ORDER_CANCEL, fingerprint).isPresent()) {
            return detail(findOrder(purchaseOrderId));
        }
        PurchaseOrder order = findOrder(purchaseOrderId);
        if (request == null) {
            ensureDraft(order, "Only DRAFT purchase orders can be cancelled without a body; "
                    + "send {expectedVersion, reason} to cancel a confirmed order");
        } else {
            ensureVersion(order, request.expectedVersion());
            if (!order.canCancel()) {
                throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                        "Only a DRAFT or CONFIRMED purchase order with nothing received can be cancelled; "
                                + "close the remainder instead");
            }
        }
        ensureNoPendingReceipt(order);
        idempotencyRecords.record(key, PurchasingCommand.PURCHASE_ORDER_CANCEL, purchaseOrderId, fingerprint);
        String reason = request == null ? null : request.reason().trim();
        Instant at = now();
        UUID actor = currentActor();
        order.cancel(reason, at, actor);
        planningLinkService.releasePurchaseOrder(purchaseOrderId,
                reason == null ? "Purchase order cancelled" : reason, at, actor);
        return detail(purchaseOrderRepository.saveAndFlush(order));
    }

    /**
     * {@code POST /{id}/close} (G7a): the open remainder of a CONFIRMED/PARTIALLY_RECEIVED order is
     * given up — every line's open quantity becomes {@code closedQuantity}. Received goods, stock and
     * QC are untouched, and the planning quota is <em>not</em> released (FE Q13): giving the whole
     * ordered quantity back would let it be converted again in full.
     */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #purchaseOrderId)")
    @Auditable(action = AuditAction.PURCHASE_ORDER_CLOSED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse close(UUID purchaseOrderId, PurchaseOrderReasonRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        PurchasingIdempotency.Fingerprint fingerprint = new PurchasingIdempotency.Fingerprint(purchaseOrderId, request);
        if (idempotencyRecords.replay(key, PurchasingCommand.PURCHASE_ORDER_CLOSE, fingerprint).isPresent()) {
            return detail(findOrder(purchaseOrderId));
        }
        PurchaseOrder order = findOrder(purchaseOrderId);
        ensureVersion(order, request.expectedVersion());
        if (!order.canClose()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a CONFIRMED or PARTIALLY_RECEIVED purchase order with an open quantity can be closed");
        }
        ensureNoPendingReceipt(order);
        idempotencyRecords.record(key, PurchasingCommand.PURCHASE_ORDER_CLOSE, purchaseOrderId, fingerprint);
        order.close(request.reason().trim(), now(), currentActor());
        return detail(purchaseOrderRepository.saveAndFlush(order));
    }

    /**
     * Drafts a purchase order for a planning conversion (G6b, D9): {@code DRAFT}, never sent, no
     * price. The number is server-generated ({@code PO-MRP-xxxxxxxx}).
     *
     * <p>G7a (FE §1.4): converting a BUY proposal also needs {@code PERM_PURCHASE_ORDER_MANAGE} on the
     * plant — the planning permission alone no longer creates purchase orders. The only caller is the
     * planning conversion service, which reaches this method through the Spring proxy, so the check
     * runs and a denial rolls the whole conversion back. Supplier, warehouse and items are validated
     * exactly as on {@link #create}.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.PURCHASE_ORDER_CREATED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse createFromPlanning(UUID companyId,
                                                    UUID plantId,
                                                    UUID warehouseId,
                                                    UUID supplierId,
                                                    LocalDate orderDate,
                                                    String note,
                                                    List<PlanningPurchaseLine> lines) {
        LocalDate earliest = lines.stream()
                .map(PlanningPurchaseLine::expectedDate)
                .min(LocalDate::compareTo)
                .orElse(orderDate);
        PurchaseOrder order = buildOrder(
                companyId,
                plantId,
                warehouseId,
                supplierService.findActiveSupplier(supplierId),
                nextPlanningOrderNo(companyId),
                orderDate,
                earliest.isBefore(orderDate) ? orderDate : earliest,
                null,
                trimToNull(note));
        for (PlanningPurchaseLine line : lines) {
            Item item = itemLookupService.getActiveItem(line.itemId());
            ensureItemBelongsToCompany(item, order.getCompany());
            order.getLines().add(PurchaseOrderLine.builder()
                    .purchaseOrder(order)
                    .item(item)
                    .orderedQuantity(requirePositive(line.orderedQuantity(), "Ordered quantity"))
                    .receivedQuantity(BigDecimal.ZERO)
                    .expectedDate(line.expectedDate())
                    .build());
        }
        return mapper.toResponse(purchaseOrderRepository.saveAndFlush(order), true);
    }

    private String nextPlanningOrderNo(UUID companyId) {
        while (true) {
            String candidate = "PO-MRP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
            if (!purchaseOrderRepository.existsByCompanyCompanyIdAndPurchaseOrderNo(companyId, candidate)) {
                return candidate;
            }
        }
    }

    /**
     * {@code PO-000001}… from {@code purchase_order_number_seq}. A value an older, client-chosen
     * number already took in this company is skipped rather than failing the save.
     */
    private String nextDraftOrderNo(UUID companyId) {
        while (true) {
            String candidate = String.format(Locale.ROOT, "PO-%06d", purchaseOrderRepository.nextOrderNumber());
            if (!purchaseOrderRepository.existsByCompanyCompanyIdAndPurchaseOrderNo(companyId, candidate)) {
                return candidate;
            }
        }
    }

    @Transactional
    PurchaseOrderResponse createFromRequisition(PurchaseRequisition requisition,
                                                Supplier supplier,
                                                PurchaseRequisitionConvertToOrderRequest request) {
        PurchaseOrder order = buildOrder(
                requisition.getCompany().getCompanyId(),
                requisition.getPlant().getPlantId(),
                requisition.getWarehouse().getWarehouseId(),
                supplier,
                normalizeCode(request.purchaseOrderNo(), "Purchase order number"),
                request.orderDate(),
                request.expectedDate(),
                requisition,
                trimToNull(request.note()));
        for (PurchaseRequisitionLine requisitionLine : requisition.getLines()) {
            PurchaseOrderLine line = PurchaseOrderLine.builder()
                    .purchaseOrder(order)
                    .purchaseRequisitionLine(requisitionLine)
                    .item(requisitionLine.getItem())
                    .orderedQuantity(requirePositive(requisitionLine.effectiveApprovedQuantity(), "Approved quantity"))
                    .receivedQuantity(BigDecimal.ZERO)
                    .expectedDate(request.expectedDate())
                    .build();
            order.getLines().add(line);
        }
        return mapper.toResponse(purchaseOrderRepository.save(order), true);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder findOrder(UUID purchaseOrderId) {
        return purchaseOrderRepository.findWithDetailsByPurchaseOrderId(purchaseOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase order", purchaseOrderId));
    }

    /** Detail projection: lines with their planning sources, {@code sourceLocked}, history. */
    private PurchaseOrderResponse detail(PurchaseOrder order) {
        Map<UUID, List<PurchaseOrderPlanningSource>> planningSources = planningLinkService.findSources(
                order.getLines().stream().map(PurchaseOrderLine::getPurchaseOrderLineId).toList());
        Map<UUID, List<PurchaseOrderLineSourceResponse>> sources = new HashMap<>();
        boolean locked = false;
        for (Map.Entry<UUID, List<PurchaseOrderPlanningSource>> entry : planningSources.entrySet()) {
            sources.put(entry.getKey(), entry.getValue().stream().map(PurchaseOrderService::toSourceResponse).toList());
            locked = locked || entry.getValue().stream().anyMatch(source -> source.releasedAt() == null);
        }
        return mapper.toResponse(order, true, sources, locked, history(order));
    }

    private static PurchaseOrderLineSourceResponse toSourceResponse(PurchaseOrderPlanningSource source) {
        return new PurchaseOrderLineSourceResponse(
                source.conversionId(),
                source.mrpRunId(),
                source.mrpRunCode(),
                source.requirementLineId(),
                source.supplySuggestionId(),
                source.sourceVersion(),
                source.convertedQuantity(),
                source.convertedAt(),
                source.releasedAt(),
                source.releaseReason());
    }

    private List<PurchaseOrderHistoryResponse> history(PurchaseOrder order) {
        Set<UUID> actors = new HashSet<>();
        for (UUID actor : Arrays.asList(order.getCreatedBy(), order.getConfirmedBy(), order.getCancelledBy(), order.getClosedBy())) {
            if (actor != null) {
                actors.add(actor);
            }
        }
        Map<UUID, String> usernames = actors.isEmpty() ? Map.of() : userLookupService.findUsernames(actors);
        List<PurchaseOrderHistoryResponse> history = new ArrayList<>();
        history.add(event("CREATE", null, PurchaseOrderStatus.DRAFT, order.getCreatedBy(), order.getCreatedAt(), null, usernames));
        if (order.getConfirmedAt() != null) {
            history.add(event("CONFIRM", PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.CONFIRMED,
                    order.getConfirmedBy(), order.getConfirmedAt(), null, usernames));
        }
        if (order.getCancelledAt() != null) {
            history.add(event("CANCEL", order.getPreviousStatus(), PurchaseOrderStatus.CANCELLED,
                    order.getCancelledBy(), order.getCancelledAt(), order.getStatusReason(), usernames));
        }
        if (order.getClosedAt() != null) {
            history.add(event("CLOSE", order.getPreviousStatus(), PurchaseOrderStatus.CLOSED,
                    order.getClosedBy(), order.getClosedAt(), order.getStatusReason(), usernames));
        }
        return history;
    }

    private static PurchaseOrderHistoryResponse event(String action,
                                                      PurchaseOrderStatus from,
                                                      PurchaseOrderStatus to,
                                                      UUID actor,
                                                      Instant at,
                                                      String reason,
                                                      Map<UUID, String> usernames) {
        return new PurchaseOrderHistoryResponse(action, from == null ? null : from.name(), to.name(),
                actor, actor == null ? null : usernames.get(actor), at, reason);
    }

    private PurchaseOrder buildOrder(UUID companyId,
                                     UUID plantId,
                                     UUID warehouseId,
                                     Supplier supplier,
                                     String purchaseOrderNo,
                                     LocalDate orderDate,
                                     LocalDate expectedDate,
                                     PurchaseRequisition sourceRequisition,
                                     String note) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        Warehouse warehouse = organizationLookupService.getActiveWarehouse(warehouseId);
        ensurePlantBelongsToCompany(plant, company);
        ensureWarehouseBelongsToPlant(warehouse, plant);
        ensureSupplierBelongsToCompany(supplier, company);
        if (purchaseOrderRepository.existsByCompanyCompanyIdAndPurchaseOrderNo(companyId, purchaseOrderNo)) {
            throw ExceptionFactory.alreadyExists(
                    ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Purchase order number", purchaseOrderNo);
        }
        if (orderDate.isAfter(expectedDate)) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Order date cannot be after expected date");
        }
        return PurchaseOrder.builder()
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .supplier(supplier)
                .purchaseOrderNo(purchaseOrderNo)
                .orderDate(orderDate)
                .expectedDate(expectedDate)
                .sourceRequisition(sourceRequisition)
                .note(note)
                .build();
    }

    private PurchaseOrderLine buildManualLine(PurchaseOrder order, PurchaseOrderLineRequest request) {
        Item item = itemLookupService.getActiveItem(request.itemId());
        ensureItemBelongsToCompany(item, order.getCompany());
        return PurchaseOrderLine.builder()
                .purchaseOrder(order)
                .purchaseRequisitionLine(null)
                .item(item)
                .orderedQuantity(requirePositive(request.orderedQuantity(), "Ordered quantity"))
                .receivedQuantity(BigDecimal.ZERO)
                .unitPrice(validateOptionalNonNegative(request.unitPrice(), "Unit price"))
                .currencyCode(normalizeCurrency(request.currencyCode()))
                .expectedDate(request.expectedDate() == null ? order.getExpectedDate() : request.expectedDate())
                .build();
    }

    private static PurchaseOrderLine applyDraftLine(PurchaseOrderLine line, Item item, PurchaseOrderDraftLineRequest request) {
        line.setItem(item);
        line.setOrderedQuantity(request.orderedQuantity());
        line.setUnitPrice(request.unitPrice());
        line.setCurrencyCode(CURRENCY_VND);
        line.setExpectedDate(request.expectedDate());
        return line;
    }

    /** Field errors that need no lookup: each line's expected date against the order date. */
    private static Map<String, String> validateDraftLines(LocalDate orderDate, List<PurchaseOrderDraftLineRequest> lines) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).expectedDate().isBefore(orderDate)) {
                errors.put("lines[" + i + "].expectedDate", "must not be before orderDate");
            }
        }
        return errors;
    }

    private static void failOnErrors(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
    }

    private static void failOnItemErrors(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.OPERATION_NOT_ALLOWED, errors);
        }
    }

    private static LocalDate earliestExpectedDate(List<PurchaseOrderDraftLineRequest> lines) {
        return lines.stream().map(PurchaseOrderDraftLineRequest::expectedDate).min(LocalDate::compareTo).orElseThrow();
    }

    /**
     * ACTIVE, same company, purchasable type, not serial-tracked. Inactive/other-company keep their
     * dedicated codes ({@code RESOURCE_INACTIVE}/{@code RESOURCE_SCOPE_MISMATCH}); a wrong type is
     * collected per line and reported together.
     */
    private Item purchasableItem(UUID itemId, Company company, int index, Map<String, String> errors) {
        Item item = itemLookupService.getActiveItem(itemId);
        ensureItemBelongsToCompany(item, company);
        if (!PURCHASABLE_TYPES.contains(item.getType())) {
            errors.put("lines[" + index + "].itemId", "item type " + item.getType() + " cannot be purchased here");
        } else if (item.isSerialTracked()) {
            errors.put("lines[" + index + "].itemId", "serial-tracked items cannot be received through a purchase order yet");
        }
        return item;
    }

    /** FE Q7: what a planning-sourced draft may not change. Field errors name every offending field. */
    private void ensureSourceFieldsUnchanged(PurchaseOrder order,
                                             PurchaseOrderUpdateRequest request,
                                             Map<UUID, PurchaseOrderLine> existing) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!order.getWarehouse().getWarehouseId().equals(request.warehouseId())) {
            errors.put("warehouseId", "comes from planning and cannot be changed");
        }
        Set<UUID> requested = request.lines().stream().map(PurchaseOrderDraftLineRequest::id)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (request.lines().stream().anyMatch(line -> line.id() == null) || !requested.equals(existing.keySet())) {
            errors.put("lines", "the lines come from planning; none can be added or removed");
        }
        for (int i = 0; i < request.lines().size(); i++) {
            PurchaseOrderDraftLineRequest lineRequest = request.lines().get(i);
            PurchaseOrderLine line = lineRequest.id() == null ? null : existing.get(lineRequest.id());
            if (line == null) {
                continue;
            }
            if (!line.getItem().getItemId().equals(lineRequest.itemId())) {
                errors.put("lines[" + i + "].itemId", "comes from planning and cannot be changed");
            }
            if (line.getOrderedQuantity().compareTo(lineRequest.orderedQuantity()) != 0) {
                errors.put("lines[" + i + "].orderedQuantity", "comes from planning and cannot be changed");
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.SOURCE_LOCKED, errors);
        }
    }

    private static List<PurchaseOrderLine> sortedLines(PurchaseOrder order) {
        return order.getLines().stream().sorted(PurchasingMapper.LINE_ORDER).toList();
    }

    /** G7b (FE Q11): a DRAFT goods receipt must be posted or cancelled before the order is cancelled or closed. */
    private void ensureNoPendingReceipt(PurchaseOrder order) {
        if (goodsReceiptRepository.existsByPurchaseOrderPurchaseOrderIdAndStatus(
                order.getPurchaseOrderId(), GoodsReceiptStatus.DRAFT)) {
            throw ExceptionFactory.custom(BusinessErrorCode.PENDING_GOODS_RECEIPT,
                    "Purchase order " + order.getPurchaseOrderNo() + " still has a draft goods receipt; post or cancel it first");
        }
    }

    private static void ensureVersion(PurchaseOrder order, Long expectedVersion) {
        if (!Objects.equals(order.getVersion(), expectedVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Purchase order version is " + order.getVersion() + ", not " + expectedVersion + "; reload it");
        }
    }

    private UUID currentActor() {
        return auditorAware.getCurrentAuditor().orElse(null);
    }

    /** Microseconds: what TIMESTAMPTZ keeps — the response must equal a later read (§0.51 #3). */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private PurchaseRequisition findRequisition(UUID requisitionId) {
        return purchaseRequisitionRepository.findWithDetailsByPurchaseRequisitionId(requisitionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase requisition", requisitionId));
    }

    private void ensureDraft(PurchaseOrder order, String message) {
        if (!order.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, message);
        }
    }

    private void ensurePlantBelongsToCompany(Plant plant, Company company) {
        if (!plant.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Plant must belong to company");
        }
    }

    private void ensureWarehouseBelongsToPlant(Warehouse warehouse, Plant plant) {
        if (!warehouse.getPlant().getPlantId().equals(plant.getPlantId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Warehouse must belong to plant");
        }
    }

    private void ensureSupplierBelongsToCompany(Supplier supplier, Company company) {
        if (!supplier.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Supplier must belong to company");
        }
    }

    private void ensureItemBelongsToCompany(Item item, Company company) {
        if (!item.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Item must belong to company");
        }
    }

    private BigDecimal requirePositive(BigDecimal quantity, String fieldName) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " must be greater than zero");
        }
        return quantity;
    }

    private BigDecimal validateOptionalNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " cannot be negative");
        }
        return value;
    }

    private String normalizeCurrency(String currencyCode) {
        return StringUtils.hasText(currencyCode) ? currencyCode.trim().toUpperCase() : null;
    }

    private String normalizeCode(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
