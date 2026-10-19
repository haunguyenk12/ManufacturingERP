package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.model.AuditChangeMode;
import com.erp.manufacturing.common.audit.model.AuditOperation;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.LotSplitCommand;
import com.erp.manufacturing.module.inventory.service.LotSplitResult;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptLineRepository;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseQualityDecisionRepository;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * G7c: purchase QC by quantity (FE §3.3, D5–D7). A decision on one POSTED, on-hold goods receipt line
 * accepts and/or rejects part of what is still pending; several decisions per line are allowed.
 *
 * <ul>
 *   <li><b>Tracked line:</b> the accepted part moves into a new {@code AVAILABLE} child lot and the
 *       rejected part into a new {@code REJECTED} child lot ({@code LOT_SPLIT_OUT}/{@code _IN}), each
 *       with the parent and the decision as source; what is still pending stays on the parent
 *       {@code HOLD} lot. An expired lot can only be rejected.</li>
 *   <li><b>Non-tracked line:</b> the accepted part leaves the pool's quality hold and becomes
 *       available; the rejected part stays on hold, owned by the receipt line (D7). No ledger row —
 *       on-hand does not move and the decision is the audit event (same as production QC, §0.13 #7).</li>
 * </ul>
 * On-hand never changes (FE §3.4), the purchase order is never touched, and the receipt's version is
 * bumped so a second decision prepared on the same version is refused.
 */
@Service
@RequiredArgsConstructor
public class PurchaseQualityService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptLineRepository goodsReceiptLineRepository;
    private final PurchaseQualityDecisionRepository decisionRepository;
    private final InventoryMovementService inventoryMovementService;
    private final ItemLookupService itemLookupService;
    private final PurchasingIdempotency idempotencyRecords;
    private final UserLookupService userLookupService;
    private final AuditorAware<UUID> auditorAware;
    private final EntityManager entityManager;

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_PURCHASE_QC_MANAGE', #goodsReceiptId)")
    @Auditable(action = AuditAction.PURCHASE_QC_DECIDED, entityType = "PurchaseQualityDecision",
               entityIdExpression = "decision.decisionId.toString()",
               changeMode = AuditChangeMode.NONE, operation = AuditOperation.CREATE)
    public PurchaseQcDecisionResult decide(UUID goodsReceiptId, UUID goodsReceiptLineId,
                                           PurchaseQcDecisionRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        PurchasingIdempotency.Fingerprint fingerprint = new PurchasingIdempotency.Fingerprint(goodsReceiptLineId, request);
        Optional<UUID> replayed = idempotencyRecords.replay(key, PurchasingCommand.PURCHASE_QC_DECISION, fingerprint);
        if (replayed.isPresent()) {
            PurchaseQualityDecision decision = findDecision(replayed.get());
            GoodsReceipt receipt = decision.getGoodsReceipt();
            return result(decision, receipt, decision.getGoodsReceiptLine());
        }

        BigDecimal accepted = request.acceptedQuantity();
        BigDecimal rejected = request.rejectedQuantity();
        String reason = trimToNull(request.reason());
        Map<String, String> inputErrors = new LinkedHashMap<>();
        if (accepted.signum() == 0 && rejected.signum() == 0) {
            inputErrors.put("acceptedQuantity", "accepted and rejected quantities cannot both be 0");
        }
        if (rejected.signum() > 0 && reason == null) {
            inputErrors.put("reason", "is required when a quantity is rejected");
        }
        if (!inputErrors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, inputErrors);
        }

        GoodsReceipt receipt = lockReceipt(goodsReceiptId);
        if (!Objects.equals(receipt.getVersion(), request.expectedVersion())) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Goods receipt version is " + receipt.getVersion() + ", not " + request.expectedVersion() + "; reload it");
        }
        if (receipt.getStatus() != GoodsReceiptStatus.POSTED || !receipt.isQualityHeld()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a posted goods receipt that was received on hold goes through purchase QC");
        }
        GoodsReceiptLine line = receipt.getLines().stream()
                .filter(candidate -> candidate.getGoodsReceiptLineId().equals(goodsReceiptLineId))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Goods receipt line", goodsReceiptLineId));
        BigDecimal pending = line.pendingQuantity();
        if (accepted.add(rejected).compareTo(pending) > 0) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, Map.of(
                    "acceptedQuantity", "accepted + rejected exceeds what is still pending on the line: "
                            + pending.stripTrailingZeros().toPlainString()));
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        InventoryLot parent = line.getLot();
        if (parent != null && accepted.signum() > 0
                && parent.getExpiresAt() != null && !parent.getExpiresAt().isAfter(now)) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.LOT_NOT_ELIGIBLE, Map.of(
                    "acceptedQuantity", "lot " + parent.getLotCode() + " expired at " + parent.getExpiresAt()
                            + "; it can only be rejected"));
        }

        UUID decisionId = UUID.randomUUID();
        idempotencyRecords.record(key, PurchasingCommand.PURCHASE_QC_DECISION, decisionId, fingerprint);
        UUID itemId = line.getItem().getItemId();
        UUID warehouseId = receipt.getWarehouse().getWarehouseId();
        LotSplitResult acceptedSplit = null;
        LotSplitResult rejectedSplit = null;
        if (parent != null) {
            if (accepted.signum() > 0) {
                acceptedSplit = split(itemId, warehouseId, parent, "A", LotStatus.AVAILABLE, accepted, decisionId, reason);
            }
            if (rejected.signum() > 0) {
                rejectedSplit = split(itemId, warehouseId, parent, "R", LotStatus.REJECTED, rejected, decisionId, reason);
            }
        } else if (accepted.signum() > 0) {
            // The rejected part simply stays in qualityHoldQuantity: unusable, and still on hand.
            inventoryMovementService.releaseQualityHold(itemId, warehouseId, accepted);
        }

        line.recordQualityDecision(accepted, rejected);
        // Lines are an inverse collection: without dirtying the header its version would not move (B112).
        receipt.setUpdatedAt(now);
        PurchaseQualityDecision decision = decisionRepository.save(PurchaseQualityDecision.builder()
                .decisionId(decisionId)
                .goodsReceipt(receipt)
                .goodsReceiptLine(line)
                .acceptedQuantity(accepted)
                .rejectedQuantity(rejected)
                .reason(reason)
                .acceptedLot(acceptedSplit == null ? null : acceptedSplit.childLot())
                .rejectedLot(rejectedSplit == null ? null : rejectedSplit.childLot())
                .acceptedMovement(acceptedSplit == null ? null : acceptedSplit.inMovement())
                .rejectedMovement(rejectedSplit == null ? null : rejectedSplit.inMovement())
                .decidedAt(now)
                .decidedBy(auditorAware.getCurrentAuditor().orElse(null))
                .build());
        GoodsReceipt saved = goodsReceiptRepository.saveAndFlush(receipt);
        return result(decision, saved, line);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PURCHASE_QC_READ', 'PLANT', #plantId)")
    public PageResult<PurchaseQcQueueLineResponse> queue(UUID companyId, UUID plantId, UUID warehouseId, String search,
                                                        LocalDate receivedDateFrom, LocalDate receivedDateTo,
                                                        Pageable pageable) {
        if (receivedDateFrom != null && receivedDateTo != null && receivedDateFrom.isAfter(receivedDateTo)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("receivedDateFrom", "must not be after receivedDateTo"));
        }
        Page<GoodsReceiptLine> page = goodsReceiptLineRepository.searchQualityQueue(companyId, plantId, warehouseId,
                trimToNull(search), receivedDateFrom, receivedDateTo, GoodsReceiptStatus.POSTED, pageable);
        return PageResult.from(page.map(this::toQueueLine));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_PURCHASE_QC_READ', #goodsReceiptId)"
            + " or @purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_GOODS_RECEIPT_READ', #goodsReceiptId)"
            + " or @purchasingPermissionGuard.hasReceiptAccess(authentication, 'PERM_PURCHASE_ORDER_READ', #goodsReceiptId)")
    public PageResult<PurchaseQcDecisionResponse> decisions(UUID goodsReceiptId, Pageable pageable) {
        Page<PurchaseQualityDecision> page = decisionRepository.findByReceipt(goodsReceiptId, pageable);
        Map<UUID, String> usernames = userLookupService.findUsernames(page.getContent().stream()
                .map(PurchaseQualityDecision::getDecidedBy).filter(Objects::nonNull).toList());
        return PageResult.from(page.map(decision -> toResponse(decision, usernames)));
    }

    private GoodsReceipt lockReceipt(UUID goodsReceiptId) {
        goodsReceiptRepository.lockRowByGoodsReceiptId(goodsReceiptId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Goods receipt", goodsReceiptId));
        // The guard already loaded the receipt graph into the request's EntityManager (open-session-in-view):
        // re-read it, lines included, under the lock — B194.
        GoodsReceipt receipt = entityManager.find(GoodsReceipt.class, goodsReceiptId);
        receipt.getLines().size();
        entityManager.refresh(receipt);
        return receipt;
    }

    private LotSplitResult split(UUID itemId, UUID warehouseId, InventoryLot parent, String side, LotStatus status,
                                 BigDecimal quantity, UUID decisionId, String reason) {
        return inventoryMovementService.splitLot(new LotSplitCommand(itemId, warehouseId, parent.getLotId(),
                        childLotCode(itemId, parent.getLotCode(), side), status, quantity,
                        PurchaseQcLookupService.LOT_SOURCE_TYPE, decisionId, reason),
                "PQC-" + decisionId + ":" + side);
    }

    /**
     * {@code <parent>-A1}, {@code <parent>-R1}, then {@code -A2}… for the next decision — the first
     * suffix no lot of the item uses yet, ignoring case (FE Q28). Fits the 120-character column.
     */
    private String childLotCode(UUID itemId, String parentCode, String side) {
        for (int index = 1; ; index++) {
            String suffix = "-" + side + index;
            String base = parentCode.length() + suffix.length() > 120
                    ? parentCode.substring(0, 120 - suffix.length())
                    : parentCode;
            String candidate = base + suffix;
            if (!itemLookupService.lotCodeExistsIgnoreCase(itemId, candidate)) {
                return candidate;
            }
        }
    }

    private PurchaseQcDecisionResult result(PurchaseQualityDecision decision, GoodsReceipt receipt, GoodsReceiptLine line) {
        Map<UUID, String> usernames = decision.getDecidedBy() == null
                ? Map.of()
                : userLookupService.findUsernames(List.of(decision.getDecidedBy()));
        return new PurchaseQcDecisionResult(toResponse(decision, usernames), receipt.getVersion(),
                line.getReceivedQuantity(), line.getAcceptedQuantity(), line.getRejectedQuantity(),
                line.pendingQuantity());
    }

    private PurchaseQcDecisionResponse toResponse(PurchaseQualityDecision decision, Map<UUID, String> usernames) {
        GoodsReceiptLine line = decision.getGoodsReceiptLine();
        List<PurchaseQcChildLotResponse> childLots = new ArrayList<>();
        List<UUID> movementIds = new ArrayList<>();
        addChild(childLots, movementIds, decision.getAcceptedLot(), LotStatus.AVAILABLE,
                decision.getAcceptedQuantity(), decision.getAcceptedMovement());
        addChild(childLots, movementIds, decision.getRejectedLot(), LotStatus.REJECTED,
                decision.getRejectedQuantity(), decision.getRejectedMovement());
        return new PurchaseQcDecisionResponse(
                decision.getDecisionId(),
                decision.getGoodsReceipt().getGoodsReceiptId(),
                decision.getGoodsReceipt().getReceiptNo(),
                line.getGoodsReceiptLineId(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                line.getItem().getUnit(),
                PurchasingMapper.trackingPolicy(line.getItem()),
                decision.getAcceptedQuantity(),
                decision.getRejectedQuantity(),
                decision.getReason(),
                decision.getDecidedBy(),
                decision.getDecidedBy() == null ? null : usernames.get(decision.getDecidedBy()),
                decision.getDecidedAt(),
                line.getLot() == null ? line.getGoodsReceiptLineId() : null,
                childLots,
                movementIds);
    }

    private static void addChild(List<PurchaseQcChildLotResponse> childLots, List<UUID> movementIds, InventoryLot lot,
                                 LotStatus status, BigDecimal quantity, StockMovement movement) {
        if (lot == null) {
            return;
        }
        UUID movementId = movement == null ? null : movement.getMovementId();
        // parentLotId is a read-only column mirror: empty on a child lot created in this same transaction.
        UUID parentLotId = lot.getParentLot() != null ? lot.getParentLot().getLotId() : lot.getParentLotId();
        childLots.add(new PurchaseQcChildLotResponse(lot.getLotId(), lot.getLotCode(), parentLotId,
                status.name(), quantity, lot.getExpiresAt(), movementId));
        if (movementId != null) {
            movementIds.add(movementId);
        }
    }

    private PurchaseQcQueueLineResponse toQueueLine(GoodsReceiptLine line) {
        GoodsReceipt receipt = line.getGoodsReceipt();
        PurchaseOrder order = receipt.getPurchaseOrder();
        InventoryLot lot = line.getLot();
        return new PurchaseQcQueueLineResponse(
                receipt.getGoodsReceiptId(),
                receipt.getReceiptNo(),
                receipt.getVersion(),
                receipt.getReceivedDate(),
                receipt.getPostedAt(),
                order.getPurchaseOrderId(),
                order.getPurchaseOrderNo(),
                order.getSupplier().getSupplierId(),
                order.getSupplier().getCode(),
                order.getSupplier().getName(),
                receipt.getWarehouse().getWarehouseId(),
                receipt.getWarehouse().getCode(),
                receipt.getWarehouse().getName(),
                line.getGoodsReceiptLineId(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                line.getItem().getUnit(),
                PurchasingMapper.trackingPolicy(line.getItem()),
                lot == null ? null : lot.getLotId(),
                lot == null ? null : lot.getLotCode(),
                line.getExpiresOn(),
                lot == null ? null : lot.getExpiresAt(),
                line.getReceivedQuantity(),
                line.getAcceptedQuantity(),
                line.getRejectedQuantity(),
                line.pendingQuantity(),
                lot == null ? line.getGoodsReceiptLineId() : null);
    }

    private PurchaseQualityDecision findDecision(UUID decisionId) {
        return decisionRepository.findWithDetailsByDecisionId(decisionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase QC decision", decisionId));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
