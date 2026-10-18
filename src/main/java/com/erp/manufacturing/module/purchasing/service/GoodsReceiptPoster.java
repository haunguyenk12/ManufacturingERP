package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrder;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderLine;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Validation and stock posting shared by the legacy create-and-post receipt and the G7b draft post.
 *
 * <p>The caller must have <b>locked the purchase order</b> ({@link #lockOrder}) before reading its
 * lines, so the open quantities read here are the latest committed ones (D11). Every
 * line goes in on hold (D4): a new lot is created {@code HOLD}, stock without a lot is added to
 * {@code qualityHoldQuantity}. Nothing becomes available before purchase QC.
 */
@Component
@RequiredArgsConstructor
class GoodsReceiptPoster {

    static final String REFERENCE_TYPE = "GOODS_RECEIPT";

    private final InventoryMovementService inventoryMovementService;
    private final ItemLookupService itemLookupService;
    private final IdempotencySupport idempotency;
    private final EntityManager entityManager;
    private final PurchaseOrderRepository purchaseOrderRepository;

    /**
     * Locks the purchase order row and re-reads it — lines included, refresh cascades through
     * {@code CascadeType.ALL} — in one step, so the open quantities are the committed ones (D11).
     *
     * <p>An entity {@code @Lock} query is not enough here. With open-session-in-view the
     * {@code @PreAuthorize} guard has already loaded the receipt graph — purchase order and lines
     * included — into the request's EntityManager before the transaction started. A locking query, or
     * a {@code refresh} with a lock mode, that meets an already-managed entity checks its version
     * against the row instead of re-reading it, so the loser of two concurrent posts died with 409
     * {@code CONCURRENT_MODIFICATION} rather than seeing the committed open quantity (smoke G7b B11).
     * Hence: lock the row id-only, then a plain {@code refresh}.
     *
     * <p>The guard reaches the purchase order lines through the receipt lines, so they can be managed
     * while the order's own {@code lines} collection is still uninitialised — and refresh does not
     * cascade into an uninitialised collection. Initialise it first, or the lines stay stale and the
     * loser dies on the line version instead (smoke G7b B11, second run).
     */
    PurchaseOrder lockOrder(UUID purchaseOrderId) {
        purchaseOrderRepository.lockRowByPurchaseOrderId(purchaseOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase order", purchaseOrderId));
        PurchaseOrder order = entityManager.find(PurchaseOrder.class, purchaseOrderId);
        order.getLines().size();
        entityManager.refresh(order);
        return order;
    }

    /** Re-reads an entity loaded before the lock was taken (see {@link #lockOrder}). */
    void refresh(Object entity) {
        entityManager.refresh(entity);
    }

    /**
     * One line to receive. {@code lotId} only from the legacy request; {@code expiresOn} only from a
     * draft. {@code index} is the line's position for field errors ({@code lines[index]}).
     */
    record Line(int index, PurchaseOrderLine orderLine, BigDecimal quantity, UUID lotId, String lotCode,
                LocalDate expiresOn) {}

    /**
     * Field-level checks that need no stock write: tracking shape, lots, expiry and the cumulative
     * quantity per purchase order line against what is still open. Draft saves run it too, for early
     * feedback; posting runs it again under the lock.
     *
     * @param newLotsOnly G7b drafts (FE Q28): every lot code must be new for the item, ignoring case.
     *                    The legacy path may still name an existing lot, but only one on HOLD — a
     *                    receipt into an AVAILABLE lot would skip purchase QC.
     */
    void validate(List<Line> lines, boolean newLotsOnly, LocalDate today) {
        Map<String, String> tracking = new LinkedHashMap<>();
        Map<String, String> lotConflicts = new LinkedHashMap<>();
        Set<String> seenCodes = new HashSet<>();
        for (Line line : lines) {
            Item item = line.orderLine().getItem();
            String field = "lines[" + line.index() + "]";
            String code = StringUtils.hasText(line.lotCode()) ? line.lotCode().trim() : null;
            if (item.isSerialTracked()) {
                tracking.put(field + ".purchaseOrderLineId", "serial-tracked items cannot be received through a purchase order yet");
            } else if (item.isLotTracked()) {
                if (line.lotId() == null && code == null) {
                    tracking.put(field + ".lotCode", "is required for a lot-tracked item");
                } else if (code != null && !seenCodes.add(item.getItemId() + "|" + code.toLowerCase(Locale.ROOT))) {
                    tracking.put(field + ".lotCode", "is used by another line of this receipt");
                } else if (newLotsOnly && code != null && itemLookupService.lotCodeExistsIgnoreCase(item.getItemId(), code)) {
                    lotConflicts.put(field + ".lotCode", "already exists for this item");
                } else if (!newLotsOnly) {
                    InventoryLot existing = line.lotId() != null
                            ? itemLookupService.getLotForItem(item, line.lotId())
                            : itemLookupService.findLotByCode(item.getItemId(), code).orElse(null);
                    if (existing != null && existing.getStatus() != LotStatus.HOLD) {
                        lotConflicts.put(field + (line.lotId() != null ? ".lotId" : ".lotCode"),
                                "lot is " + existing.getStatus() + "; a purchase receipt only adds to a lot on HOLD");
                    }
                }
                if (line.expiresOn() != null && (!line.expiresOn().isAfter(today))) {
                    tracking.put(field + ".expiresOn", "must be after today and after the received date");
                }
            } else if (line.lotId() != null || code != null || line.expiresOn() != null) {
                tracking.put(field + ".lotCode", "item is not lot-tracked; omit lotCode and expiresOn");
            }
        }
        if (!tracking.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, tracking);
        }
        if (!lotConflicts.isEmpty()) {
            throw ExceptionFactory.withErrors(newLotsOnly ? BusinessErrorCode.LOT_CODE_ALREADY_EXISTS
                    : BusinessErrorCode.LOT_NOT_ELIGIBLE, lotConflicts);
        }
        ensureWithinOpen(lines);
    }

    /**
     * Receives every line on hold and moves the purchase order's received quantities and status.
     * Returns the receive result of each line, in the order given.
     */
    List<InventoryMovementResult> post(PurchaseOrder order,
                                       List<Line> lines,
                                       boolean newLotsOnly,
                                       String baseKey,
                                       String reason,
                                       String referenceId,
                                       LocalDate today,
                                       Instant at) {
        if (!order.canReceive()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only CONFIRMED or PARTIALLY_RECEIVED purchase orders can receive goods");
        }
        validate(lines, newLotsOnly, today);
        ZoneId zone = ZoneId.of(order.getPlant().getTimezone());
        List<InventoryMovementResult> results = new ArrayList<>();
        for (Line line : lines) {
            InventoryMovementResult result = inventoryMovementService.receive(
                    new InventoryReceiveCommand(
                            line.orderLine().getItem().getItemId(),
                            order.getWarehouse().getWarehouseId(),
                            line.lotId(),
                            StringUtils.hasText(line.lotCode()) ? line.lotCode().trim() : null,
                            line.quantity(),
                            reason,
                            REFERENCE_TYPE,
                            referenceId,
                            null,
                            null,
                            line.expiresOn() == null ? null : line.expiresOn().atStartOfDay(zone).toInstant()),
                    idempotency.childKey(baseKey, line.index() + 1),
                    LotStatus.HOLD,
                    !newLotsOnly);
            line.orderLine().receive(line.quantity());
            results.add(result);
        }
        order.refreshReceiptStatus();
        // Received quantities live on the lines (an inverse collection): touch the header so the
        // order's version moves with every posted receipt (bẫy B112).
        order.setUpdatedAt(at);
        return results;
    }

    /** Cumulative per purchase order line — several receipt lines may split one order line into lots. */
    private static void ensureWithinOpen(List<Line> lines) {
        Map<UUID, BigDecimal> requested = new HashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        for (Line line : lines) {
            PurchaseOrderLine orderLine = line.orderLine();
            BigDecimal total = requested.merge(orderLine.getPurchaseOrderLineId(), line.quantity(), BigDecimal::add);
            if (total.compareTo(orderLine.openQuantity()) > 0) {
                errors.put("lines[" + line.index() + "].receivedQuantity",
                        "exceeds what is still open on the purchase order line: "
                                + orderLine.openQuantity().stripTrailingZeros().toPlainString());
            }
        }
        if (!errors.isEmpty()) {
            // B27: a quantity-vs-document conflict — the same code the legacy path always used.
            throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, errors);
        }
    }
}
