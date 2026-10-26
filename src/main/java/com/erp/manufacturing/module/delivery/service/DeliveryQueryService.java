package com.erp.manufacturing.module.delivery.service;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.delivery.domain.Delivery;
import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.domain.DeliveryStatus;
import com.erp.manufacturing.module.delivery.dto.DeliveryCandidateLineResponse;
import com.erp.manufacturing.module.delivery.dto.DeliveryCandidatesResponse;
import com.erp.manufacturing.module.delivery.dto.DeliveryEventResponse;
import com.erp.manufacturing.module.delivery.dto.DeliveryLineResponse;
import com.erp.manufacturing.module.delivery.dto.DeliveryResponse;
import com.erp.manufacturing.module.delivery.dto.DeliverySourceResponse;
import com.erp.manufacturing.module.delivery.repository.DeliveryLineRepository;
import com.erp.manufacturing.module.delivery.repository.DeliveryRepository;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.service.InventoryLotLookupService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.StockPositionRef;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * G10b reads. Labels for a page come from batch lookups (C14): the orders and their lines, warehouses, lot codes and
 * usernames — one query each, never one per row.
 */
@Service
@RequiredArgsConstructor
public class DeliveryQueryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryLineRepository deliveryLineRepository;
    private final DeliverySupport support;
    private final SalesOrderCommercialLookupService salesOrderLookupService;
    private final SalesOrderStockReservationService reservationService;
    private final InventoryMovementService inventoryMovementService;
    private final InventoryLotLookupService lotLookupService;
    private final OrganizationLookupService organizationLookupService;
    private final UserLookupService userLookupService;
    private final PermissionGuard permissionGuard;
    private final SecurityAuditorAware auditorAware;

    @Transactional(readOnly = true)
    @PreAuthorize("@deliveryPermissionGuard.canReadDelivery(authentication, #deliveryId)")
    public DeliveryResponse get(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Delivery", deliveryId));
        return toResponses(List.of(delivery)).get(0);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@deliveryPermissionGuard.canReadPlant(authentication, #plantId)")
    public PageResult<DeliveryResponse> list(UUID plantId, DeliveryStatus status, UUID salesOrderId, UUID customerId,
                                             UUID warehouseId, LocalDate fromDate, LocalDate toDate, String search,
                                             Pageable pageable) {
        Page<Delivery> page = deliveryRepository.search(plantId, status, salesOrderId, customerId, warehouseId,
                fromDate, toDate, StringUtils.hasText(search) ? search.trim() : null, pageable);
        return PageResult.from(new PageImpl<>(toResponses(page.getContent()), pageable, page.getTotalElements()));
    }

    /** POSTED deliveries of the plant that took stock from {@code lotId} (FE "related", one source filter — Q38). */
    @Transactional(readOnly = true)
    @PreAuthorize("@deliveryPermissionGuard.canReadPlant(authentication, #plantId)")
    public PageResult<DeliveryResponse> related(UUID plantId, UUID lotId, Pageable pageable) {
        Page<Delivery> page = deliveryRepository.findPostedFromLots(plantId, List.of(lotId), pageable);
        return PageResult.from(new PageImpl<>(toResponses(page.getContent()), pageable, page.getTotalElements()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@deliveryPermissionGuard.canReadDelivery(authentication, #deliveryId)")
    public PageResult<DeliveryEventResponse> history(UUID deliveryId, Pageable pageable) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Delivery", deliveryId));
        List<DeliveryEventResponse> raw = Stream.of(
                        new DeliveryEventResponse("CREATED", delivery.getCreatedBy(), null, null, delivery.getCreatedAt()),
                        new DeliveryEventResponse("POSTED", delivery.getPostedBy(), null, null, delivery.getPostedAt()),
                        new DeliveryEventResponse("CANCELLED", delivery.getCancelledBy(), null,
                                delivery.getCancelReason(), delivery.getCancelledAt()))
                .filter(event -> event.at() != null)
                .sorted(Comparator.comparing(DeliveryEventResponse::at))
                .toList();
        Map<UUID, String> usernames = userLookupService.findUsernames(
                raw.stream().map(DeliveryEventResponse::actorId).filter(Objects::nonNull).distinct().toList());
        List<DeliveryEventResponse> rows = raw.stream()
                .map(e -> new DeliveryEventResponse(e.action(), e.actorId(),
                        e.actorId() == null ? null : usernames.get(e.actorId()), e.reason(), e.at()))
                .toList();
        return page(rows, pageable);
    }

    /** FE R04: the order's lines with D, the claims and what can still be claimed; no money. */
    @Transactional(readOnly = true)
    @PreAuthorize("@deliveryPermissionGuard.canReadOrder(authentication, #salesOrderId)")
    public DeliveryCandidatesResponse candidates(UUID salesOrderId, UUID editingDeliveryId, Pageable pageable) {
        SalesOrder order = salesOrderLookupService.getOrder(salesOrderId);
        List<SalesOrderLine> lines = order.getLines().stream()
                .sorted(Comparator.comparing(SalesOrderLine::getLineNo)).toList();
        List<UUID> ids = lines.stream().map(SalesOrderLine::getSalesOrderLineId).toList();
        boolean known = order.isDeliveryBaselineKnown();
        Map<UUID, BigDecimal> delivered = support.delivered(ids);
        Map<UUID, BigDecimal> others = support.draftClaims(ids, editingDeliveryId);
        Map<UUID, BigDecimal> own = editingDeliveryId == null ? Map.of() : ownClaims(editingDeliveryId, salesOrderId);
        Map<UUID, BigDecimal> held = reservationService.sumActiveHeldBySalesOrderLineIds(ids);
        List<DeliveryCandidateLineResponse> rows = lines.stream().map(line -> {
            UUID id = line.getSalesOrderLineId();
            BigDecimal d = delivered.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal other = others.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal available = line.getOrderedQuantity().subtract(d).subtract(other);
            return new DeliveryCandidateLineResponse(id, line.getLineNo(), line.getItem().getItemId(),
                    line.getItem().getCode(), line.getItem().getName(), line.getItem().getUnit(), line.getDueDate(),
                    line.getOrderedQuantity(),
                    known ? d : null,
                    known ? own.getOrDefault(id, BigDecimal.ZERO) : null,
                    known ? other : null,
                    known ? available.max(BigDecimal.ZERO) : null,
                    held.getOrDefault(id, BigDecimal.ZERO));
        }).toList();
        String blocked = support.blockedReason(order);
        return new DeliveryCandidatesResponse(order.getSalesOrderId(), order.getOrderNo(), order.getStatus().name(),
                order.getVersion(), order.getCustomerId(), order.getCustomerName(), blocked == null, blocked,
                page(rows, pageable));
    }

    /**
     * FE R04/R08: positions of the line's item in the order's plant — free stock (FEFO) and wherever the line itself
     * holds stock. Another order's hold never shows here (A09). An order that cannot be delivered still lists its
     * positions, with {@code availableForLine} 0.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@deliveryPermissionGuard.canReadOrder(authentication, #salesOrderId)")
    public PageResult<DeliverySourceResponse> sources(UUID salesOrderId, UUID salesOrderLineId, UUID editingDeliveryId,
                                                      UUID warehouseId, Pageable pageable) {
        SalesOrder order = salesOrderLookupService.getOrder(salesOrderId);
        SalesOrderLine line = order.getLines().stream()
                .filter(l -> l.getSalesOrderLineId().equals(salesOrderLineId))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales order line", salesOrderLineId));
        UUID plantId = order.getPlant().getPlantId();
        List<UUID> warehouseIds = warehouseId != null
                ? List.of(organizationLookupService.getActiveWarehouseInPlant(warehouseId, plantId).getWarehouseId())
                : organizationLookupService.resolveScope(ScopeResourceType.PLANT, plantId).warehouseIds();
        UUID itemId = line.getItem().getItemId();
        Instant now = Instant.now();

        Map<StockPositionRef, StockBalance> positions = new LinkedHashMap<>();
        if (!warehouseIds.isEmpty()) {
            for (StockBalance balance : reservationService.findCandidates(itemId, warehouseIds, Pageable.unpaged())) {
                positions.put(positionOf(balance), balance);
            }
        }
        List<MaterialReservation> holds = reservationService.findActiveForSalesOrder(salesOrderId).stream()
                .filter(hold -> salesOrderLineId.equals(hold.getSalesOrderLineId()))
                .filter(hold -> warehouseIds.contains(hold.getWarehouse().getWarehouseId()))
                .toList();
        for (MaterialReservation hold : holds) {
            StockPositionRef position = new StockPositionRef(itemId, hold.getWarehouse().getWarehouseId(),
                    hold.getLot() == null ? null : hold.getLot().getLotId());
            if (!positions.containsKey(position)) {
                inventoryMovementService.findPosition(position).ifPresent(balance -> positions.put(position, balance));
            }
        }

        BigDecimal claimable = BigDecimal.ZERO;
        if (support.blockedReason(order) == null) {
            List<UUID> ids = List.of(salesOrderLineId);
            claimable = line.getOrderedQuantity()
                    .subtract(support.delivered(ids).getOrDefault(salesOrderLineId, BigDecimal.ZERO))
                    .subtract(support.draftClaims(ids, editingDeliveryId).getOrDefault(salesOrderLineId, BigDecimal.ZERO))
                    .max(BigDecimal.ZERO);
        }
        Map<UUID, Warehouse> warehouses = organizationLookupService.findWarehousesById(
                positions.keySet().stream().map(StockPositionRef::warehouseId).distinct().toList());
        List<DeliverySourceResponse> rows = new ArrayList<>();
        for (Map.Entry<StockPositionRef, StockBalance> entry : positions.entrySet()) {
            StockBalance balance = entry.getValue();
            if (balance.getLot() != null && !DeliverySupport.eligible(balance.getLot(), now)) {
                continue;
            }
            BigDecimal free = DeliverySupport.freeQuantity(balance, now);
            BigDecimal ownHeld = DeliverySupport.ownHeld(holds, salesOrderLineId, entry.getKey());
            BigDecimal usable = free.add(ownHeld).min(claimable);
            Warehouse warehouse = warehouses.get(entry.getKey().warehouseId());
            rows.add(new DeliverySourceResponse(
                    DeliverySupport.sourceKey(entry.getKey().warehouseId(), entry.getKey().lotId()),
                    entry.getKey().warehouseId(),
                    warehouse == null ? null : warehouse.getCode(),
                    warehouse == null ? null : warehouse.getName(),
                    itemId,
                    line.getItem().getUnit(),
                    entry.getKey().lotId(),
                    balance.getLot() == null ? null : balance.getLot().getLotCode(),
                    balance.getLot() == null ? null : balance.getLot().getExpiresAt(),
                    free,
                    ownHeld,
                    usable));
        }
        rows.sort(Comparator.comparing((DeliverySourceResponse r) -> r.expiresAt() == null ? Instant.MAX : r.expiresAt())
                .thenComparing(r -> r.warehouseCode() == null ? "" : r.warehouseCode())
                .thenComparing(r -> r.lotCode() == null ? "" : r.lotCode()));
        return page(rows, pageable);
    }

    private Map<UUID, BigDecimal> ownClaims(UUID deliveryId, UUID salesOrderId) {
        return deliveryRepository.findById(deliveryId)
                .filter(delivery -> delivery.isDraft() && delivery.getSalesOrderId().equals(salesOrderId))
                .map(delivery -> deliveryLineRepository.findByDeliveryDeliveryIdOrderByLineNo(deliveryId).stream()
                        .collect(Collectors.toMap(DeliveryLine::getSalesOrderLineId, DeliveryLine::getQuantity, BigDecimal::add)))
                .orElse(Map.of());
    }

    private List<DeliveryResponse> toResponses(List<Delivery> deliveries) {
        if (deliveries.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<DeliveryLine>> linesByDelivery = deliveryLineRepository
                .findByDeliveryDeliveryIdInOrderByLineNo(deliveries.stream().map(Delivery::getDeliveryId).toList())
                .stream()
                .collect(Collectors.groupingBy(line -> line.getDelivery().getDeliveryId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, SalesOrder> orders = salesOrderLookupService.findOrders(
                deliveries.stream().map(Delivery::getSalesOrderId).distinct().toList());
        Map<UUID, SalesOrderLine> orderLines = orders.values().stream()
                .flatMap(order -> order.getLines().stream())
                .collect(Collectors.toMap(SalesOrderLine::getSalesOrderLineId, Function.identity()));
        List<DeliveryLine> allLines = linesByDelivery.values().stream().flatMap(List::stream).toList();
        Map<UUID, Warehouse> warehouses = organizationLookupService.findWarehousesById(
                allLines.stream().map(DeliveryLine::getWarehouseId).distinct().toList());
        Map<UUID, String> lotCodes = lotLookupService.findLotCodes(
                allLines.stream().map(DeliveryLine::getLotId).filter(Objects::nonNull).distinct().toList());
        Map<UUID, String> usernames = userLookupService.findUsernames(deliveries.stream()
                .flatMap(d -> Stream.of(d.getCreatedBy(), d.getPostedBy(), d.getCancelledBy()))
                .filter(Objects::nonNull).distinct().toList());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Map<String, Boolean> permissions = new java.util.HashMap<>();

        List<DeliveryResponse> responses = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            List<DeliveryLine> lines = linesByDelivery.getOrDefault(delivery.getDeliveryId(), List.of());
            boolean posted = delivery.getStatus() == DeliveryStatus.POSTED;
            List<DeliveryLineResponse> lineResponses = lines.stream().map(line -> {
                SalesOrderLine orderLine = orderLines.get(line.getSalesOrderLineId());
                Warehouse warehouse = warehouses.get(line.getWarehouseId());
                return new DeliveryLineResponse(line.getDeliveryLineId(), line.getLineNo(), line.getSalesOrderLineId(),
                        orderLine == null ? null : orderLine.getLineNo(), line.getItemId(),
                        orderLine == null ? null : orderLine.getItem().getCode(),
                        orderLine == null ? null : orderLine.getItem().getName(),
                        orderLine == null ? null : orderLine.getItem().getUnit(),
                        line.getWarehouseId(), warehouse == null ? null : warehouse.getCode(),
                        line.getLotId(), line.getLotId() == null ? null : lotCodes.get(line.getLotId()),
                        DeliverySupport.sourceKey(line.getWarehouseId(), line.getLotId()),
                        line.getQuantity(),
                        posted ? line.getReservedQuantity() : null,
                        posted ? line.getQuantity().subtract(line.getReservedQuantity()) : null,
                        line.getReservedMovementId(), line.getFreeMovementId());
            }).toList();
            BigDecimal total = lines.stream().map(DeliveryLine::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            SalesOrder order = orders.get(delivery.getSalesOrderId());
            responses.add(new DeliveryResponse(delivery.getDeliveryId(), delivery.getCode(), DeliveryResponse.TYPE,
                    delivery.getStatus().name(), delivery.getVersion(), delivery.getCompanyId(), delivery.getPlantId(),
                    delivery.getSalesOrderId(), order == null ? null : order.getOrderNo(), delivery.getCustomerId(),
                    delivery.getCustomerName(), delivery.getDocumentDate(), delivery.getNote(), total, lineResponses,
                    DeliveryResponse.PENDING_COSTING,
                    delivery.getCreatedBy(), usernames.get(delivery.getCreatedBy()), delivery.getCreatedAt(),
                    delivery.getPostedBy(), delivery.getPostedBy() == null ? null : usernames.get(delivery.getPostedBy()),
                    delivery.getPostedAt(),
                    delivery.getCancelledBy(), delivery.getCancelledBy() == null ? null : usernames.get(delivery.getCancelledBy()),
                    delivery.getCancelledAt(), delivery.getCancelReason(),
                    allowedActions(delivery, authentication, actor, permissions)));
        }
        return responses;
    }

    /**
     * UX hint only (FE §3.3 capabilities): EDIT/CANCEL for the author with MANAGE, POST for POST authority. Each
     * (permission, plant) is checked once per response, not once per row (C14).
     */
    private List<String> allowedActions(Delivery delivery, Authentication authentication, UUID actor,
                                        Map<String, Boolean> permissions) {
        if (!delivery.isDraft() || authentication == null) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        if (actor != null && actor.equals(delivery.getCreatedBy())
                && has(authentication, DeliveryPermissionGuard.MANAGE, delivery.getPlantId(), permissions)) {
            actions.add("EDIT");
            actions.add("CANCEL");
        }
        if (has(authentication, DeliveryPermissionGuard.POST, delivery.getPlantId(), permissions)) {
            actions.add("POST");
        }
        return actions;
    }

    private boolean has(Authentication authentication, String permission, UUID plantId, Map<String, Boolean> cache) {
        return cache.computeIfAbsent(permission + "@" + plantId,
                k -> permissionGuard.hasResourceAccess(authentication, permission, "PLANT", plantId));
    }

    private static StockPositionRef positionOf(StockBalance balance) {
        return new StockPositionRef(balance.getItem().getItemId(), balance.getWarehouse().getWarehouseId(),
                balance.getLot() == null ? null : balance.getLot().getLotId());
    }

    private static <T> PageResult<T> page(List<T> rows, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return PageResult.from(new PageImpl<>(rows.subList(from, to), pageable, rows.size()));
    }
}
