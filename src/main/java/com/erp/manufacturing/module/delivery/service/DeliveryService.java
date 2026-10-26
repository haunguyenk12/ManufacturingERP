package com.erp.manufacturing.module.delivery.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.delivery.domain.Delivery;
import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.dto.DeliveryCancelRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryCreateRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryPostRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryResponse;
import com.erp.manufacturing.module.delivery.dto.DeliveryUpdateRequest;
import com.erp.manufacturing.module.delivery.repository.DeliveryLineRepository;
import com.erp.manufacturing.module.delivery.repository.DeliveryRepository;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryIssueCommand;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.StockPositionRef;
import com.erp.manufacturing.module.sales.domain.CommercialCommand;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.dto.CommercialCommandResult;
import com.erp.manufacturing.module.sales.service.CommercialIdempotency;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.service.ReasonRules;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * G10b: deliveries — DRAFT claims quota on the order lines, POST moves the stock (FE R05–R09). Every command:
 * {@code Idempotency-Key} replay first, then the order lock, then version, state and the rules of
 * {@link DeliverySupport}, then the write, all in one transaction.
 *
 * <p>Concurrency: every command that claims or consumes an order's quota locks the sales order row first
 * ({@link SalesOrderCommercialLookupService#lockOrder}), so two drafts or two posts on the same order queue instead of
 * both reading the same remaining quantity. Posting also locks the stock positions, in one stable order, before
 * reading them ({@link InventoryMovementService#lockPositions}).
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    static final String REFERENCE_TYPE = "DELIVERY";

    private final DeliveryRepository deliveryRepository;
    private final DeliveryLineRepository deliveryLineRepository;
    private final DeliverySupport support;
    private final SalesOrderCommercialLookupService salesOrderLookupService;
    private final SalesOrderStockReservationService reservationService;
    private final InventoryMovementService inventoryMovementService;
    private final CommercialIdempotency idempotency;
    private final SecurityAuditorAware auditorAware;
    private final EntityManager entityManager;

    @Transactional
    @PreAuthorize("@deliveryPermissionGuard.canManageOrder(authentication, #request.salesOrderId())")
    @Auditable(action = AuditAction.DELIVERY_CREATED, entityType = "Delivery", entityIdExpression = "#result.documentId().toString()")
    public CommercialCommandResult create(DeliveryCreateRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(null, request);
        var replayed = idempotency.replay(key, CommercialCommand.DELIVERY_CREATE, fingerprint);
        if (replayed.isPresent()) {
            return result(findDelivery(replayed.get()));
        }
        SalesOrder order = salesOrderLookupService.lockOrder(request.salesOrderId());
        if (!Objects.equals(order.getVersion(), request.expectedOrderVersion())) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Sales order version is " + order.getVersion() + ", not " + request.expectedOrderVersion() + "; reload it");
        }
        support.ensureDeliverable(order);
        support.ensureDate(order, request.documentDate());
        List<DeliverySupport.ResolvedLine> lines = support.resolve(order, request.lines());
        support.ensureQuota(lines, null);
        support.ensureStock(order, lines, reservationService.findActiveForSalesOrder(order.getSalesOrderId()));

        Delivery delivery = deliveryRepository.saveAndFlush(Delivery.builder()
                .code(String.format("DN-%06d", deliveryRepository.nextNumber()))
                .companyId(order.getCompany().getCompanyId())
                .plantId(order.getPlant().getPlantId())
                .salesOrderId(order.getSalesOrderId())
                .customerId(order.getCustomerId())
                .customerName(order.getCustomerName())
                .documentDate(request.documentDate())
                .note(trimToNull(request.note()))
                .build());
        saveLines(delivery, lines);
        idempotency.record(key, CommercialCommand.DELIVERY_CREATE, delivery.getDeliveryId(), fingerprint);
        return result(delivery);
    }

    /** Full replacement of a DRAFT (FE R06): its own claim is excluded from Cd, so editing 3 → 2 → 3 never double-counts. */
    @Transactional
    @PreAuthorize("@deliveryPermissionGuard.hasDeliveryAccess(authentication, 'PERM_DELIVERY_MANAGE', #deliveryId)")
    @Auditable(action = AuditAction.DELIVERY_UPDATED, entityType = "Delivery", entityIdExpression = "#deliveryId.toString()")
    public CommercialCommandResult update(UUID deliveryId, DeliveryUpdateRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(deliveryId, request);
        if (idempotency.replay(key, CommercialCommand.DELIVERY_UPDATE, fingerprint).isPresent()) {
            return result(findDelivery(deliveryId));
        }
        Delivery delivery = lockedDraft(deliveryId, request.expectedVersion(), "edit");
        SalesOrder order = salesOrderLookupService.getOrder(delivery.getSalesOrderId());
        support.ensureDeliverable(order);
        support.ensureDate(order, request.documentDate());
        List<DeliverySupport.ResolvedLine> lines = support.resolve(order, request.lines());
        support.ensureQuota(lines, deliveryId);
        support.ensureStock(order, lines, reservationService.findActiveForSalesOrder(order.getSalesOrderId()));

        delivery.setDocumentDate(request.documentDate());
        delivery.setNote(trimToNull(request.note()));
        // Lines live in their own table: dirty the header so the version moves (B112) — before the bulk delete, whose
        // automatic flush would otherwise write a changed note first and leave a second UPDATE for this one (B212).
        delivery.setUpdatedAt(Instant.now());
        deliveryLineRepository.deleteByDeliveryId(deliveryId);
        saveLines(delivery, lines);
        Delivery saved = deliveryRepository.saveAndFlush(delivery);
        idempotency.record(key, CommercialCommand.DELIVERY_UPDATE, deliveryId, fingerprint);
        return result(saved);
    }

    /**
     * FE R07–R09: re-checks every rule against the current state under the locks, then for each line issues first
     * from the order line's own holds at that position ({@code :R}, consuming the holds oldest first) and the rest
     * from free stock ({@code :F}). Both legs refuse an expired lot. The order's version moves by one; the stock,
     * the holds, the delivered quantity and the delivery commit or roll back together. Whoever has
     * {@code PERM_DELIVERY_POST} may post, the author included (FE Q28).
     */
    @Transactional
    @PreAuthorize("@deliveryPermissionGuard.hasDeliveryAccess(authentication, 'PERM_DELIVERY_POST', #deliveryId)")
    @Auditable(action = AuditAction.DELIVERY_POSTED, entityType = "Delivery", entityIdExpression = "#deliveryId.toString()")
    public CommercialCommandResult post(UUID deliveryId, DeliveryPostRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(deliveryId, request);
        if (idempotency.replay(key, CommercialCommand.DELIVERY_POST, fingerprint).isPresent()) {
            return result(findDelivery(deliveryId));
        }
        Delivery delivery = findDelivery(deliveryId);
        SalesOrder order = salesOrderLookupService.lockOrder(delivery.getSalesOrderId());
        entityManager.refresh(delivery);
        ensureVersion(delivery, request.expectedVersion());
        ensureDraft(delivery);
        support.ensureDeliverable(order);
        List<DeliveryLine> stored = deliveryLineRepository.findByDeliveryDeliveryIdOrderByLineNo(deliveryId);
        List<DeliverySupport.ResolvedLine> lines = support.resolveStored(order, stored);
        inventoryMovementService.lockPositions(lines.stream().map(DeliverySupport.ResolvedLine::position).toList());
        support.ensureQuota(lines, deliveryId);
        List<MaterialReservation> holds = reservationService.findActiveForSalesOrder(order.getSalesOrderId());
        support.ensureStock(order, lines, holds);

        for (int i = 0; i < stored.size(); i++) {
            issue(delivery, stored.get(i), lines.get(i), holds);
        }
        deliveryLineRepository.saveAll(stored);
        delivery.post(Instant.now().truncatedTo(ChronoUnit.MICROS), currentActor());
        Delivery saved = deliveryRepository.saveAndFlush(delivery);
        salesOrderLookupService.touch(order);
        idempotency.record(key, CommercialCommand.DELIVERY_POST, deliveryId, fingerprint);
        return result(saved);
    }

    /** A DRAFT only, by its author, with a reason; returns the claim once (FE R06). POSTED is final (FE Q33). */
    @Transactional
    @PreAuthorize("@deliveryPermissionGuard.hasDeliveryAccess(authentication, 'PERM_DELIVERY_MANAGE', #deliveryId)")
    @Auditable(action = AuditAction.DELIVERY_CANCELLED, entityType = "Delivery", entityIdExpression = "#deliveryId.toString()")
    public CommercialCommandResult cancel(UUID deliveryId, DeliveryCancelRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(deliveryId, request);
        if (idempotency.replay(key, CommercialCommand.DELIVERY_CANCEL, fingerprint).isPresent()) {
            return result(findDelivery(deliveryId));
        }
        String reason = ReasonRules.lengthChecked(request.reason(), "reason");
        Delivery delivery = lockedDraft(deliveryId, request.expectedVersion(), "cancel");
        delivery.cancel(Instant.now().truncatedTo(ChronoUnit.MICROS), currentActor(), reason);
        Delivery saved = deliveryRepository.saveAndFlush(delivery);
        idempotency.record(key, CommercialCommand.DELIVERY_CANCEL, deliveryId, fingerprint);
        return result(saved);
    }

    private void issue(Delivery delivery, DeliveryLine line, DeliverySupport.ResolvedLine resolved,
                       List<MaterialReservation> holds) {
        StockPositionRef position = resolved.position();
        BigDecimal outstanding = line.getQuantity();
        BigDecimal fromHolds = BigDecimal.ZERO;
        List<MaterialReservation> consumed = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        for (MaterialReservation hold : holds) {
            if (outstanding.signum() <= 0) {
                break;
            }
            if (!DeliverySupport.isAt(hold, line.getSalesOrderLineId(), position)
                    || hold.remainingQuantity().signum() <= 0) {
                continue;
            }
            BigDecimal take = hold.remainingQuantity().min(outstanding);
            consumed.add(hold);
            amounts.add(take);
            fromHolds = fromHolds.add(take);
            outstanding = outstanding.subtract(take);
        }
        String prefix = "DN:" + delivery.getDeliveryId() + ":L" + line.getLineNo();
        if (fromHolds.signum() > 0) {
            StockMovement movement = inventoryMovementService.issueReservedManual(command(delivery, line, fromHolds),
                    prefix + ":R").movement();
            for (int i = 0; i < consumed.size(); i++) {
                reservationService.consumeForDelivery(consumed.get(i), amounts.get(i));
            }
            line.setReservedMovementId(movement.getMovementId());
        }
        if (outstanding.signum() > 0) {
            StockMovement movement = inventoryMovementService.issueManual(command(delivery, line, outstanding),
                    prefix + ":F").movement();
            line.setFreeMovementId(movement.getMovementId());
        }
        line.setReservedQuantity(fromHolds);
    }

    private static InventoryIssueCommand command(Delivery delivery, DeliveryLine line, BigDecimal quantity) {
        return new InventoryIssueCommand(line.getItemId(), line.getWarehouseId(), line.getLotId(), null, quantity,
                "Delivery " + delivery.getCode(), REFERENCE_TYPE, delivery.getDeliveryId().toString(), null, null);
    }

    private void saveLines(Delivery delivery, List<DeliverySupport.ResolvedLine> lines) {
        List<DeliveryLine> rows = new ArrayList<>();
        for (DeliverySupport.ResolvedLine line : lines) {
            rows.add(DeliveryLine.builder()
                    .delivery(delivery)
                    .lineNo(line.index() + 1)
                    .salesOrderLineId(line.orderLine().getSalesOrderLineId())
                    .itemId(line.item().getItemId())
                    .warehouseId(line.warehouse().getWarehouseId())
                    .lotId(line.lot() == null ? null : line.lot().getLotId())
                    .quantity(line.quantity())
                    .build());
        }
        deliveryLineRepository.saveAll(rows);
    }

    /** Locks the order first (quota is per order), then re-reads the delivery the guard may have loaded (B194). */
    private Delivery lockedDraft(UUID deliveryId, Long expectedVersion, String action) {
        Delivery delivery = findDelivery(deliveryId);
        salesOrderLookupService.lockOrder(delivery.getSalesOrderId());
        entityManager.refresh(delivery);
        ensureVersion(delivery, expectedVersion);
        ensureDraft(delivery);
        UUID actor = currentActor();
        if (actor == null || !actor.equals(delivery.getCreatedBy())) {
            throw ExceptionFactory.custom(AuthErrorCode.ACCESS_DENIED,
                    "Only the person who created the delivery can " + action + " it");
        }
        return delivery;
    }

    private static void ensureVersion(Delivery delivery, Long expectedVersion) {
        if (!Objects.equals(delivery.getVersion(), expectedVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Delivery version is " + delivery.getVersion() + ", not " + expectedVersion + "; reload it");
        }
    }

    private static void ensureDraft(Delivery delivery) {
        if (!delivery.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Delivery " + delivery.getCode() + " is " + delivery.getStatus() + "; only a DRAFT can change");
        }
    }

    private Delivery findDelivery(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Delivery", deliveryId));
    }

    private UUID currentActor() {
        return auditorAware.getCurrentAuditor().orElse(null);
    }

    private static CommercialCommandResult result(Delivery delivery) {
        return CommercialCommandResult.of(delivery.getDeliveryId(), DeliveryResponse.TYPE, delivery.getVersion());
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
