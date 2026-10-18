package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderLineRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * G7b: goods receipt drafts (D3). A DRAFT is saved and edited without touching stock and without
 * holding any of the order's open quantity (FE Q9); {@link #post} re-checks everything under the
 * purchase order lock and receives every line on hold (D4). The legacy create-and-post endpoint
 * stays in {@link GoodsReceiptService}; both post through {@link GoodsReceiptPoster}.
 */
@Service
@RequiredArgsConstructor
public class GoodsReceiptDraftService {

    private static final List<PurchaseOrderStatus> RECEIVABLE = List.of(
            PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final OrganizationLookupService organizationLookupService;
    private final GoodsReceiptPoster poster;
    private final PurchasingIdempotency idempotencyRecords;
    private final PurchasingMapper mapper;
    private final AuditorAware<UUID> auditorAware;

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_GOODS_RECEIPT_MANAGE', #request.purchaseOrderId())")
    @Auditable(action = AuditAction.GOODS_RECEIPT_DRAFT_SAVED, entityType = "GoodsReceipt",
               entityIdExpression = "goodsReceiptId.toString()",
               warehouseId = "#result?.warehouseId()")
    public GoodsReceiptResponse createDraft(GoodsReceiptDraftCreateRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        Optional<UUID> replayed = idempotencyRecords.replay(key, PurchasingCommand.GOODS_RECEIPT_CREATE_DRAFT, request);
        if (replayed.isPresent()) {
            return detail(findReceipt(replayed.get()));
        }
        PurchaseOrder order = findOrder(request.purchaseOrderId());
        ensureReceivable(order);
        Map<String, String> idErrors = new LinkedHashMap<>();
        for (int i = 0; i < request.lines().size(); i++) {
            if (request.lines().get(i).id() != null) {
                idErrors.put("lines[" + i + "].id", "must be omitted when creating a goods receipt");
            }
        }
        failOnErrors(idErrors);
        List<GoodsReceiptPoster.Line> lines = validateDraft(order, request.receivedDate(), request.lines());

        GoodsReceipt receipt = GoodsReceipt.builder()
                .purchaseOrder(order)
                .warehouse(order.getWarehouse())
                .receiptNo(nextReceiptNo(order.getCompany().getCompanyId()))
                .status(GoodsReceiptStatus.DRAFT)
                .receivedDate(request.receivedDate())
                .reference(trimToNull(request.reference()))
                .note(trimToNull(request.notes()))
                .build();
        for (int i = 0; i < lines.size(); i++) {
            receipt.getLines().add(applyLine(GoodsReceiptLine.builder().goodsReceipt(receipt).build(),
                    lines.get(i), request.lines().get(i)));
        }
        GoodsReceipt saved = goodsReceiptRepository.saveAndFlush(receipt);
        idempotencyRecords.record(key, PurchasingCommand.GOODS_RECEIPT_CREATE_DRAFT, saved.getGoodsReceiptId(), request);
        return detail(saved);
    }

    /**
     * {@code PATCH /v1/goods-receipts/{id}}: DRAFT only, full line replacement with stable line ids.
     * The version always moves (lines are an inverse collection, bẫy B112).
     */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_GOODS_RECEIPT_MANAGE', #goodsReceiptId)")
    @Auditable(action = AuditAction.GOODS_RECEIPT_DRAFT_SAVED, entityType = "GoodsReceipt",
               entityIdExpression = "goodsReceiptId.toString()",
               warehouseId = "#result?.warehouseId()")
    public GoodsReceiptResponse updateDraft(UUID goodsReceiptId, GoodsReceiptUpdateRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        PurchasingIdempotency.Fingerprint fingerprint = new PurchasingIdempotency.Fingerprint(goodsReceiptId, request);
        if (idempotencyRecords.replay(key, PurchasingCommand.GOODS_RECEIPT_UPDATE, fingerprint).isPresent()) {
            return detail(findReceipt(goodsReceiptId));
        }
        GoodsReceipt receipt = findReceipt(goodsReceiptId);
        ensureVersion(receipt, request.expectedVersion());
        ensureDraft(receipt, "Only DRAFT goods receipts can be edited");
        Map<UUID, GoodsReceiptLine> existing = receipt.getLines().stream()
                .collect(Collectors.toMap(GoodsReceiptLine::getGoodsReceiptLineId, line -> line));
        Map<String, String> idErrors = new LinkedHashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            UUID lineId = request.lines().get(i).id();
            if (lineId == null) {
                continue;
            }
            if (!seen.add(lineId)) {
                idErrors.put("lines[" + i + "].id", "is listed more than once");
            } else if (!existing.containsKey(lineId)) {
                idErrors.put("lines[" + i + "].id", "is not a line of this goods receipt");
            }
        }
        failOnErrors(idErrors);
        PurchaseOrder order = findOrder(receipt.getPurchaseOrder().getPurchaseOrderId());
        ensureReceivable(order);
        List<GoodsReceiptPoster.Line> lines = validateDraft(order, request.receivedDate(), request.lines());
        idempotencyRecords.record(key, PurchasingCommand.GOODS_RECEIPT_UPDATE, goodsReceiptId, fingerprint);

        List<GoodsReceiptLine> kept = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            GoodsReceiptDraftLineRequest lineRequest = request.lines().get(i);
            GoodsReceiptLine line = lineRequest.id() == null ? null : existing.get(lineRequest.id());
            if (line == null) {
                line = GoodsReceiptLine.builder().goodsReceipt(receipt).build();
                receipt.getLines().add(line);
            }
            kept.add(applyLine(line, lines.get(i), lineRequest));
        }
        receipt.getLines().removeIf(line -> kept.stream().noneMatch(k -> k == line));
        receipt.setReceivedDate(request.receivedDate());
        if (request.reference() != null) {
            receipt.setReference(trimToNull(request.reference()));
        }
        if (request.notes() != null) {
            receipt.setNote(trimToNull(request.notes()));
        }
        receipt.setUpdatedAt(Instant.now());
        return detail(goodsReceiptRepository.saveAndFlush(receipt));
    }

    /**
     * {@code POST /v1/goods-receipts/{id}/post}: DRAFT → POSTED in one transaction — every line on
     * hold, the order's received quantities and status, the receipt's movements. The purchase order
     * is locked <b>before</b> the receipt or the order's lines are loaded (D11), so two receipts
     * posted at the same time against the same open quantity cannot both pass: the second one reads
     * the first one's received quantities and gets {@code PLANNED_QUANTITY_EXCEEDED} naming its line.
     */
    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_GOODS_RECEIPT_POST', #goodsReceiptId)")
    @Auditable(action = AuditAction.GOODS_RECEIPT_POSTED, entityType = "GoodsReceipt",
               entityIdExpression = "goodsReceiptId.toString()",
               warehouseId = "#result?.warehouseId()")
    public GoodsReceiptResponse post(UUID goodsReceiptId, GoodsReceiptVersionRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        PurchasingIdempotency.Fingerprint fingerprint = new PurchasingIdempotency.Fingerprint(goodsReceiptId, request);
        if (idempotencyRecords.replay(key, PurchasingCommand.GOODS_RECEIPT_POST, fingerprint).isPresent()) {
            return detail(findReceipt(goodsReceiptId));
        }
        UUID purchaseOrderId = goodsReceiptRepository.findPurchaseOrderIdByGoodsReceiptId(goodsReceiptId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Goods receipt", goodsReceiptId));
        PurchaseOrder order = poster.lockOrder(purchaseOrderId);
        GoodsReceipt receipt = findReceipt(goodsReceiptId);
        poster.refresh(receipt);
        ensureVersion(receipt, request.expectedVersion());
        ensureDraft(receipt, "Only DRAFT goods receipts can be posted");
        idempotencyRecords.record(key, PurchasingCommand.GOODS_RECEIPT_POST, goodsReceiptId, fingerprint);

        List<GoodsReceiptLine> receiptLines = receipt.getLines().stream()
                .sorted(PurchasingMapper.RECEIPT_LINE_ORDER).toList();
        List<GoodsReceiptPoster.Line> lines = new ArrayList<>();
        for (int i = 0; i < receiptLines.size(); i++) {
            GoodsReceiptLine line = receiptLines.get(i);
            lines.add(new GoodsReceiptPoster.Line(i, line.getPurchaseOrderLine(), line.getReceivedQuantity(),
                    null, line.getLotCode(), line.getExpiresOn()));
        }
        LocalDate today = today(order);
        if (receipt.getReceivedDate().isAfter(today)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("receivedDate", "must not be after today"));
        }
        Instant now = now();
        List<InventoryMovementResult> results = poster.post(order, lines, true,
                "GR-" + goodsReceiptId + ":post", receipt.getNote(), goodsReceiptId.toString(), today, now);
        for (int i = 0; i < receiptLines.size(); i++) {
            GoodsReceiptLine line = receiptLines.get(i);
            line.setStockMovement(results.get(i).movement());
            line.setLot(results.get(i).movement().getLot());
        }
        receipt.markPosted(now, auditorAware.getCurrentAuditor().orElse(null));
        purchaseOrderRepository.saveAndFlush(order);
        return detail(goodsReceiptRepository.saveAndFlush(receipt));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_GOODS_RECEIPT_READ', 'PLANT', #plantId)")
    public PageResult<GoodsReceiptResponse> list(UUID companyId,
                                                 UUID plantId,
                                                 UUID purchaseOrderId,
                                                 UUID warehouseId,
                                                 GoodsReceiptStatus status,
                                                 String search,
                                                 LocalDate receivedDateFrom,
                                                 LocalDate receivedDateTo,
                                                 Pageable pageable) {
        ensurePlantOfCompany(companyId, plantId);
        if (receivedDateFrom != null && receivedDateTo != null && receivedDateFrom.isAfter(receivedDateTo)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("receivedDateFrom", "must not be after receivedDateTo"));
        }
        return PageResult.from(goodsReceiptRepository.searchByPlant(companyId, plantId, purchaseOrderId, warehouseId,
                        status, trimToNull(search), receivedDateFrom, receivedDateTo, pageable)
                .map(receipt -> mapper.toResponse(receipt, false)));
    }

    /**
     * Purchase orders a receipt can be drafted against, each with only its still-open lines. Two
     * queries for the page (orders, then their lines) — never one per order (C13/C14).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_GOODS_RECEIPT_READ', 'PLANT', #plantId)")
    public PageResult<GoodsReceiptCandidateResponse> candidates(UUID companyId, UUID plantId, String search,
                                                                Pageable pageable) {
        ensurePlantOfCompany(companyId, plantId);
        Page<PurchaseOrder> page = purchaseOrderRepository.searchReceivingCandidates(
                companyId, plantId, RECEIVABLE, trimToNull(search), pageable);
        List<UUID> ids = page.getContent().stream().map(PurchaseOrder::getPurchaseOrderId).toList();
        Map<UUID, List<PurchaseOrderLine>> openLines = ids.isEmpty() ? Map.of()
                : purchaseOrderLineRepository.findByPurchaseOrderPurchaseOrderIdIn(ids).stream()
                    .filter(line -> line.openQuantity().signum() > 0)
                    .collect(Collectors.groupingBy(line -> line.getPurchaseOrder().getPurchaseOrderId()));
        return PageResult.from(page.map(order ->
                mapper.toCandidate(order, openLines.getOrDefault(order.getPurchaseOrderId(), List.of()))));
    }

    private List<GoodsReceiptPoster.Line> validateDraft(PurchaseOrder order, LocalDate receivedDate,
                                                        List<GoodsReceiptDraftLineRequest> requests) {
        LocalDate today = today(order);
        Map<String, String> errors = new LinkedHashMap<>();
        if (receivedDate.isBefore(order.getOrderDate())) {
            errors.put("receivedDate", "must not be before the purchase order date " + order.getOrderDate());
        } else if (receivedDate.isAfter(today)) {
            errors.put("receivedDate", "must not be after today");
        }
        Map<UUID, PurchaseOrderLine> orderLines = order.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getPurchaseOrderLineId, line -> line));
        List<GoodsReceiptPoster.Line> lines = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            GoodsReceiptDraftLineRequest request = requests.get(i);
            PurchaseOrderLine orderLine = orderLines.get(request.purchaseOrderLineId());
            if (orderLine == null) {
                errors.put("lines[" + i + "].purchaseOrderLineId", "is not a line of this purchase order");
                continue;
            }
            if (request.expiresOn() != null && !request.expiresOn().isAfter(receivedDate)) {
                errors.put("lines[" + i + "].expiresOn", "must be after the received date");
            }
            lines.add(new GoodsReceiptPoster.Line(i, orderLine, request.receivedQuantity(), null,
                    request.lotCode(), request.expiresOn()));
        }
        failOnErrors(errors);
        poster.validate(lines, true, today);
        return lines;
    }

    private static GoodsReceiptLine applyLine(GoodsReceiptLine line, GoodsReceiptPoster.Line validated,
                                              GoodsReceiptDraftLineRequest request) {
        line.setPurchaseOrderLine(validated.orderLine());
        line.setItem(validated.orderLine().getItem());
        line.setReceivedQuantity(request.receivedQuantity());
        line.setLotCode(StringUtils.hasText(request.lotCode()) ? request.lotCode().trim() : null);
        line.setExpiresOn(request.expiresOn());
        return line;
    }

    private GoodsReceiptResponse detail(GoodsReceipt receipt) {
        return mapper.toResponse(receipt, true);
    }

    private String nextReceiptNo(UUID companyId) {
        while (true) {
            String candidate = String.format(Locale.ROOT, "GR-%06d", goodsReceiptRepository.nextReceiptNumber());
            if (!goodsReceiptRepository.existsByPurchaseOrderCompanyCompanyIdAndReceiptNo(companyId, candidate)) {
                return candidate;
            }
        }
    }

    private void ensurePlantOfCompany(UUID companyId, UUID plantId) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        if (!plant.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH, "Plant must belong to company");
        }
    }

    private static void ensureReceivable(PurchaseOrder order) {
        if (!order.canReceive()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only CONFIRMED or PARTIALLY_RECEIVED purchase orders can receive goods");
        }
    }

    private static void ensureDraft(GoodsReceipt receipt, String message) {
        if (!receipt.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, message);
        }
    }

    private static void ensureVersion(GoodsReceipt receipt, Long expectedVersion) {
        if (!Objects.equals(receipt.getVersion(), expectedVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Goods receipt version is " + receipt.getVersion() + ", not " + expectedVersion + "; reload it");
        }
    }

    private static void failOnErrors(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
    }

    private GoodsReceipt findReceipt(UUID goodsReceiptId) {
        return goodsReceiptRepository.findWithDetailsByGoodsReceiptId(goodsReceiptId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Goods receipt", goodsReceiptId));
    }

    private PurchaseOrder findOrder(UUID purchaseOrderId) {
        return purchaseOrderRepository.findWithDetailsByPurchaseOrderId(purchaseOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase order", purchaseOrderId));
    }

    private static LocalDate today(PurchaseOrder order) {
        return LocalDate.now(ZoneId.of(order.getPlant().getTimezone()));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
