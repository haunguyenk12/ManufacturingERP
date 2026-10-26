package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stock held for a sales order line (G6a, NEXT_PHASE_PLAN.md §13.2 D4). The {@code sales} module owns
 * the demand side — which line, how much is still uncovered, which order status allows it — and calls
 * this service for the stock side only (rule C7).
 *
 * <p>The held quantity goes to the <b>same</b> {@code stock_balances.reserved_quantity} work-order
 * reservations use, so every availability figure (MRP, dashboard, issue/transfer/count gates, lot
 * status change) sees it without being told sales orders exist.
 *
 * <p>No {@code @PreAuthorize}: the caller ({@code SalesOrderReservationService}) has authorized
 * {@code PERM_SALES_ORDER_MANAGE} on the order's plant (FE Q4) — the same shape as
 * {@code PlanningDemandService.createFromSalesOrderLine}.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderStockReservationService {

    private final MaterialReservationRepository reservationRepository;
    private final StockBalanceRepository stockBalanceRepository;

    /**
     * Holds {@code quantity} of {@code item} in {@code warehouse} for one sales order line. With
     * {@code lotId} the hold comes from that lot only; without it, a lot-tracked item is spread FEFO
     * over the eligible lots of the warehouse (FE Q25) — one reservation row per lot, so every row
     * still names its lot. All or nothing: a shortfall reserves nothing.
     */
    @Transactional
    public List<MaterialReservation> reserve(UUID salesOrderId,
                                             UUID salesOrderLineId,
                                             Item item,
                                             Warehouse warehouse,
                                             UUID lotId,
                                             BigDecimal quantity) {
        if (item.isSerialTracked()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Serial-tracked items cannot be held for a sales order");
        }
        Instant now = Instant.now();
        List<StockBalance> sources = new ArrayList<>();
        List<BigDecimal> takes = new ArrayList<>();
        if (lotId != null || !item.isLotTracked()) {
            if (lotId != null && !item.isLotTracked()) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Item is not lot-tracked; omit lotId");
            }
            StockBalance balance = findPosition(item, warehouse, lotId);
            ensureLotEligible(balance.getLot(), now);
            if (balance.availableQuantity().compareTo(quantity) < 0) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.INSUFFICIENT_STOCK);
            }
            sources.add(balance);
            takes.add(quantity);
        } else {
            BigDecimal outstanding = quantity;
            for (StockBalance balance : stockBalanceRepository.findReservableBalancesFefo(
                    item.getItemId(), List.of(warehouse.getWarehouseId()), LotStatus.AVAILABLE, now,
                    Pageable.unpaged()).getContent()) {
                if (outstanding.signum() <= 0) {
                    break;
                }
                BigDecimal take = balance.availableQuantity().min(outstanding);
                if (take.signum() <= 0) {
                    continue;
                }
                sources.add(balance);
                takes.add(take);
                outstanding = outstanding.subtract(take);
            }
            if (outstanding.signum() > 0) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.INSUFFICIENT_STOCK);
            }
        }

        List<MaterialReservation> created = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            StockBalance balance = sources.get(i);
            BigDecimal take = takes.get(i);
            balance.reserve(take);
            stockBalanceRepository.save(balance);
            created.add(reservationRepository.save(MaterialReservation.builder()
                    .salesOrderId(salesOrderId)
                    .salesOrderLineId(salesOrderLineId)
                    .item(item)
                    .warehouse(balance.getWarehouse())
                    .lot(balance.getLot())
                    .quantity(take)
                    .consumedQuantity(BigDecimal.ZERO)
                    .status(MaterialReservationStatus.ACTIVE)
                    .build()));
        }
        return created;
    }

    /**
     * Releases one {@code ACTIVE} reservation of this sales order. Another order's or a work order's
     * reservation is "not found" here — it is never released through a sales order (FE A09).
     * Releasing does not change the lot: a lot moved to {@code HOLD} meanwhile stays unavailable
     * (FE A10) because availability on the read side is gated by lot status, not by this row.
     */
    @Transactional
    public MaterialReservation release(UUID salesOrderId, UUID reservationId) {
        MaterialReservation reservation = reservationRepository.findWithDetailsByReservationId(reservationId)
                .filter(r -> salesOrderId.equals(r.getSalesOrderId()))
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales order reservation", reservationId));
        if (reservation.getStatus() != MaterialReservationStatus.ACTIVE) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only active reservations can be released");
        }
        returnToFreeStock(reservation);
        reservation.release();
        return reservationRepository.save(reservation);
    }

    /** Cancelling the order releases every reservation it still holds, in the caller's transaction. */
    @Transactional
    public int cancelActiveForSalesOrder(UUID salesOrderId) {
        List<MaterialReservation> active = reservationRepository
                .findBySalesOrderIdAndStatus(salesOrderId, MaterialReservationStatus.ACTIVE);
        for (MaterialReservation reservation : active) {
            returnToFreeStock(reservation);
            reservation.cancel();
            reservationRepository.save(reservation);
        }
        return active.size();
    }

    /**
     * G10b: the order's {@code ACTIVE} holds, oldest first — what a delivery takes before free stock (FE Q14). One
     * query for the whole order; the caller narrows by line and position.
     */
    @Transactional(readOnly = true)
    public List<MaterialReservation> findActiveForSalesOrder(UUID salesOrderId) {
        return reservationRepository.findBySalesOrderIdAndStatus(salesOrderId, MaterialReservationStatus.ACTIVE)
                .stream()
                .sorted(java.util.Comparator.comparing(MaterialReservation::getCreatedAt,
                                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                        .thenComparing(r -> r.getReservationId().toString()))
                .toList();
    }

    /**
     * G10b: a delivery issued {@code amount} out of this hold. The balance side — quantity and reserved quantity both
     * down — is the {@code ISSUE} itself ({@code issueReservedManual}); this only moves the hold to its new remainder.
     */
    @Transactional
    public void consumeForDelivery(MaterialReservation reservation, BigDecimal amount) {
        reservation.consume(amount);
        reservationRepository.save(reservation);
    }

    /** Remaining {@code ACTIVE} held quantity per sales order line, one query (rule C14). */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> sumActiveHeldBySalesOrderLineIds(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds == null || salesOrderLineIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> held = new HashMap<>();
        for (MaterialReservationRepository.SalesLineQuantityProjection row
                : reservationRepository.sumActiveRemainingBySalesOrderLineIds(salesOrderLineIds)) {
            held.put(row.getSalesOrderLineId(), row.getQuantity());
        }
        return held;
    }

    @Transactional(readOnly = true)
    public Page<MaterialReservation> list(UUID salesOrderId,
                                          UUID salesOrderLineId,
                                          MaterialReservationStatus status,
                                          Pageable pageable) {
        return reservationRepository.searchBySalesOrder(salesOrderId, salesOrderLineId, status, pageable);
    }

    /** Positions a line could be held from: free, lot {@code AVAILABLE} and not expired (FE A08). */
    @Transactional(readOnly = true)
    public Page<StockBalance> findCandidates(UUID itemId, Collection<UUID> warehouseIds, Pageable pageable) {
        return stockBalanceRepository.findReservableBalancesFefo(
                itemId, warehouseIds, LotStatus.AVAILABLE, Instant.now(), pageable);
    }

    private StockBalance findPosition(Item item, Warehouse warehouse, UUID lotId) {
        return (lotId == null
                ? stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(
                        item.getItemId(), warehouse.getWarehouseId())
                : stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                        item.getItemId(), warehouse.getWarehouseId(), lotId))
                .orElseThrow(() -> ExceptionFactory.businessRule(BusinessErrorCode.INSUFFICIENT_STOCK,
                        "No stock of this item exists in the warehouse for that lot"));
    }

    private void ensureLotEligible(InventoryLot lot, Instant now) {
        if (lot == null) {
            return;
        }
        if (lot.getStatus() != LotStatus.AVAILABLE) {
            throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE,
                    "Lot cannot be held in status " + lot.getStatus());
        }
        if (lot.getExpiresAt() != null && !lot.getExpiresAt().isAfter(now)) {
            throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE, "Lot has expired");
        }
    }

    private void returnToFreeStock(MaterialReservation reservation) {
        BigDecimal remaining = reservation.remainingQuantity();
        if (remaining.signum() == 0) {
            return;
        }
        StockBalance balance = findPosition(reservation.getItem(), reservation.getWarehouse(),
                reservation.getLot() != null ? reservation.getLot().getLotId() : null);
        balance.releaseReserved(remaining);
        stockBalanceRepository.save(balance);
    }
}
