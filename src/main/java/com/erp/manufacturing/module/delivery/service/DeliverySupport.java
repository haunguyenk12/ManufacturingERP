package com.erp.manufacturing.module.delivery.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.dto.DeliveryLineRequest;
import com.erp.manufacturing.module.delivery.repository.DeliveryLineRepository;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.StockPositionRef;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * G10b: the rules a delivery is checked against on save and again at post (FE R05–R08). Quantities are always
 * derived from the delivery lines themselves — D (POSTED) and Cd (DRAFT, the edited one excluded) — never from a
 * counter column.
 */
@Component
@RequiredArgsConstructor
public class DeliverySupport {

    /** {@code FULFILLED} only means QC released all of it; the goods still have to leave the warehouse. */
    static final Set<SalesOrderStatus> DELIVERABLE = EnumSet.of(SalesOrderStatus.CONFIRMED,
            SalesOrderStatus.IN_PRODUCTION, SalesOrderStatus.PARTIALLY_FULFILLED, SalesOrderStatus.FULFILLED);

    private static final String KEY_SEPARATOR = "|";
    private static final String NO_LOT = "-";

    private final DeliveryLineRepository deliveryLineRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final InventoryMovementService inventoryMovementService;
    private final SalesOrderCommercialLookupService salesOrderLookupService;

    /** {@code null} when the order can be delivered; otherwise why not, in the vocabulary of the candidates screen. */
    String blockedReason(SalesOrder order) {
        if (!DELIVERABLE.contains(order.getStatus())) {
            return "ORDER_STATUS";
        }
        if (!order.isDeliveryBaselineKnown()) {
            return "BASELINE_UNKNOWN";
        }
        if (order.getCustomerId() == null) {
            return "CUSTOMER_MISSING";
        }
        return salesOrderLookupService.findCustomer(order.getCustomerId()).filter(Customer::isActive).isPresent()
                ? null : "CUSTOMER_INACTIVE";
    }

    /**
     * Status first (409 {@code STATE_CONFLICT}); then the D3/D4 gaps a legacy order cannot fill (409
     * {@code SOURCE_UNKNOWN}); then the customer still ACTIVE (422 {@code RESOURCE_INACTIVE}).
     */
    void ensureDeliverable(SalesOrder order) {
        String reason = blockedReason(order);
        if (reason == null) {
            return;
        }
        switch (reason) {
            case "ORDER_STATUS" -> throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "A sales order in status " + order.getStatus() + " cannot be delivered");
            case "CUSTOMER_INACTIVE" -> throw ExceptionFactory.custom(BusinessErrorCode.RESOURCE_INACTIVE,
                    "The order's customer is inactive");
            default -> throw ExceptionFactory.custom(BusinessErrorCode.SOURCE_UNKNOWN,
                    "This order has no known delivery baseline (" + reason + "); it cannot be delivered");
        }
    }

    Map<UUID, BigDecimal> delivered(Collection<UUID> lineIds) {
        return sums(deliveryLineRepository.sumPostedBySalesOrderLineIds(lineIds));
    }

    Map<UUID, BigDecimal> draftClaims(Collection<UUID> lineIds, UUID excludeDeliveryId) {
        return sums(deliveryLineRepository.sumDraftBySalesOrderLineIds(lineIds, excludeDeliveryId));
    }

    /** {@code documentDate} is not after the plant's today and not before the order date (FE R27). */
    void ensureDate(SalesOrder order, LocalDate documentDate) {
        LocalDate today = plantToday(order);
        if (documentDate.isAfter(today)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("documentDate", "must not be after the plant's today " + today));
        }
        if (documentDate.isBefore(order.getOrderDate())) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("documentDate", "must not be before the order date " + order.getOrderDate()));
        }
    }

    static LocalDate plantToday(SalesOrder order) {
        return LocalDate.now(ZoneId.of(order.getPlant().getTimezone()));
    }

    static String sourceKey(UUID warehouseId, UUID lotId) {
        String raw = warehouseId + KEY_SEPARATOR + (lotId == null ? NO_LOT : lotId.toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads a key this server issued; anything else is a 400 on the line's {@code sourceKey}. */
    static StockPositionRef decodeSourceKey(String key, UUID itemId, String field) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(key.trim()), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + KEY_SEPARATOR, -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException(raw);
            }
            return new StockPositionRef(itemId, UUID.fromString(parts[0]),
                    NO_LOT.equals(parts[1]) ? null : UUID.fromString(parts[1]));
        } catch (IllegalArgumentException malformed) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of(field, "is not a source key issued by delivery-sources"));
        }
    }

    /** One line, its order line and its stock position, after every master-data check. */
    record ResolvedLine(int index, SalesOrderLine orderLine, Item item, Warehouse warehouse, InventoryLot lot,
                        BigDecimal quantity) {
        StockPositionRef position() {
            return new StockPositionRef(item.getItemId(), warehouse.getWarehouseId(), lot == null ? null : lot.getLotId());
        }

        String field(String name) {
            return "lines[" + index + "]." + name;
        }
    }

    List<ResolvedLine> resolve(SalesOrder order, List<DeliveryLineRequest> requests) {
        Map<UUID, SalesOrderLine> orderLines = orderLines(order);
        List<ResolvedLine> resolved = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            DeliveryLineRequest request = requests.get(i);
            SalesOrderLine orderLine = orderLines.get(request.salesOrderLineId());
            if (orderLine == null) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                        Map.of("lines[" + i + "].salesOrderLineId", "is not a line of this sales order"));
            }
            StockPositionRef position = decodeSourceKey(request.sourceKey(), orderLine.getItem().getItemId(),
                    "lines[" + i + "].sourceKey");
            resolved.add(resolveOne(i, order, orderLine, position.warehouseId(), position.lotId(), request.quantity()));
        }
        return resolved;
    }

    /** The stored lines of a draft, re-checked against today's master data at post (FE R07). */
    List<ResolvedLine> resolveStored(SalesOrder order, List<DeliveryLine> lines) {
        Map<UUID, SalesOrderLine> orderLines = orderLines(order);
        List<ResolvedLine> resolved = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            DeliveryLine line = lines.get(i);
            resolved.add(resolveOne(i, order, orderLines.get(line.getSalesOrderLineId()),
                    line.getWarehouseId(), line.getLotId(), line.getQuantity()));
        }
        return resolved;
    }

    private ResolvedLine resolveOne(int index, SalesOrder order, SalesOrderLine orderLine, UUID warehouseId,
                                    UUID lotId, BigDecimal quantity) {
        Item item = itemLookupService.getActiveItem(orderLine.getItem().getItemId());
        if (item.isSerialTracked()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Serial-tracked items cannot be delivered yet: " + item.getCode());
        }
        String field = "lines[" + index + "].sourceKey";
        if (item.isLotTracked() != (lotId != null)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of(field,
                    item.isLotTracked() ? "a lot-tracked item is delivered from a lot" : "this item has no lots"));
        }
        Warehouse warehouse = organizationLookupService.getActiveWarehouseInPlant(warehouseId, order.getPlant().getPlantId());
        InventoryLot lot = lotId == null ? null : itemLookupService.getLotForItem(item, lotId);
        if (lot != null && !eligible(lot, Instant.now())) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.LOT_NOT_ELIGIBLE,
                    Map.of(field, "lot " + lot.getLotCode() + " is not AVAILABLE or has expired"));
        }
        return new ResolvedLine(index, orderLine, item, warehouse, lot, quantity);
    }

    /**
     * FE R05/R06: per order line, the lines together stay within ordered − D − Cd, the delivery being edited (or
     * posted) excluded from Cd. The line that crosses the remainder carries the error.
     */
    void ensureQuota(List<ResolvedLine> lines, UUID excludeDeliveryId) {
        List<UUID> ids = lines.stream().map(l -> l.orderLine().getSalesOrderLineId()).distinct().toList();
        Map<UUID, BigDecimal> delivered = delivered(ids);
        Map<UUID, BigDecimal> drafts = draftClaims(ids, excludeDeliveryId);
        Map<UUID, BigDecimal> claimed = new HashMap<>();
        for (ResolvedLine line : lines) {
            UUID id = line.orderLine().getSalesOrderLineId();
            BigDecimal remaining = line.orderLine().getOrderedQuantity()
                    .subtract(delivered.getOrDefault(id, BigDecimal.ZERO))
                    .subtract(drafts.getOrDefault(id, BigDecimal.ZERO));
            BigDecimal total = claimed.merge(id, line.quantity(), BigDecimal::add);
            if (total.compareTo(remaining) > 0) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, Map.of(line.field("quantity"),
                        "the order line can still be delivered " + remaining.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                                + " (ordered minus delivered minus other draft deliveries)"));
            }
        }
    }

    /**
     * FE R08/A10: per stock position, what the lines need beyond their own order's holds there must fit in the free
     * quantity. Each order line may only use its own holds; free stock is shared; other orders' holds are untouchable.
     */
    void ensureStock(SalesOrder order, List<ResolvedLine> lines, List<MaterialReservation> activeHolds) {
        Instant now = Instant.now();
        Map<StockPositionRef, List<ResolvedLine>> byPosition = lines.stream()
                .collect(Collectors.groupingBy(ResolvedLine::position, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<StockPositionRef, List<ResolvedLine>> entry : byPosition.entrySet()) {
            StockPositionRef position = entry.getKey();
            Map<UUID, BigDecimal> requested = new LinkedHashMap<>();
            for (ResolvedLine line : entry.getValue()) {
                requested.merge(line.orderLine().getSalesOrderLineId(), line.quantity(), BigDecimal::add);
            }
            BigDecimal needFree = BigDecimal.ZERO;
            for (Map.Entry<UUID, BigDecimal> request : requested.entrySet()) {
                BigDecimal own = ownHeld(activeHolds, request.getKey(), position);
                BigDecimal beyond = request.getValue().subtract(own);
                if (beyond.signum() > 0) {
                    needFree = needFree.add(beyond);
                }
            }
            BigDecimal free = inventoryMovementService.findPosition(position)
                    .map(balance -> freeQuantity(balance, now))
                    .orElse(BigDecimal.ZERO);
            if (needFree.compareTo(free) > 0) {
                ResolvedLine last = entry.getValue().get(entry.getValue().size() - 1);
                throw ExceptionFactory.withErrors(BusinessErrorCode.INSUFFICIENT_STOCK, Map.of(last.field("quantity"),
                        "the source has " + free.stripTrailingZeros().toPlainString()
                                + " free beyond this order's own holds"));
            }
        }
    }

    /** What this order line still holds at this exact position — warehouse and lot both. */
    static BigDecimal ownHeld(List<MaterialReservation> activeHolds, UUID salesOrderLineId, StockPositionRef position) {
        return activeHolds.stream()
                .filter(hold -> isAt(hold, salesOrderLineId, position))
                .map(MaterialReservation::remainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static boolean isAt(MaterialReservation hold, UUID salesOrderLineId, StockPositionRef position) {
        return salesOrderLineId.equals(hold.getSalesOrderLineId())
                && hold.getWarehouse().getWarehouseId().equals(position.warehouseId())
                && Objects.equals(hold.getLot() == null ? null : hold.getLot().getLotId(), position.lotId());
    }

    /** On hand minus every hold and quality hold; 0 for a lot that is not AVAILABLE or has expired. */
    static BigDecimal freeQuantity(StockBalance balance, Instant now) {
        if (balance.getLot() != null && !eligible(balance.getLot(), now)) {
            return BigDecimal.ZERO;
        }
        BigDecimal free = balance.availableQuantity();
        return free.signum() > 0 ? free : BigDecimal.ZERO;
    }

    static boolean eligible(InventoryLot lot, Instant now) {
        return lot.getStatus() == LotStatus.AVAILABLE && (lot.getExpiresAt() == null || lot.getExpiresAt().isAfter(now));
    }

    private static Map<UUID, SalesOrderLine> orderLines(SalesOrder order) {
        return order.getLines().stream().collect(Collectors.toMap(SalesOrderLine::getSalesOrderLineId, Function.identity()));
    }

    private static Map<UUID, BigDecimal> sums(List<DeliveryLineRepository.LineQuantityProjection> rows) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (DeliveryLineRepository.LineQuantityProjection row : rows) {
            result.put(row.getSalesOrderLineId(), row.getQuantity());
        }
        return result;
    }
}
