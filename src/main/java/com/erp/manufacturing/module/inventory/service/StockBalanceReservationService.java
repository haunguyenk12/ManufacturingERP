package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for modules that hold stock against a stock balance — work-order reservations, material issue
 * candidates and sales-order holds (rule C7). Before RA-1 those modules read and saved {@link StockBalance}
 * through this module's repository directly.
 *
 * <p>Only lots in {@link LotStatus#AVAILABLE} that have not expired are offered, in FEFO order — the eligibility
 * every caller already used. No {@code @Transactional}: every method runs in the caller's transaction, so a
 * read here and the {@code reserve} that follows it see the same persistence context, exactly as the direct
 * repository calls did. No {@code @PreAuthorize}: callers are authorised on their own document.
 */
@Service
@RequiredArgsConstructor
public class StockBalanceReservationService {

    private final StockBalanceRepository stockBalanceRepository;

    /** Balances a hold may take from, FEFO, paged. */
    public Page<StockBalance> findReservable(UUID itemId, Collection<UUID> warehouseIds, Instant asOf,
                                             Pageable pageable) {
        return stockBalanceRepository.findReservableBalancesFefo(
                itemId, warehouseIds, LotStatus.AVAILABLE, asOf, pageable);
    }

    /** Balances a material issue may consume, FEFO, unpaged. */
    public List<StockBalance> findIssuable(UUID itemId, Collection<UUID> warehouseIds, Instant asOf) {
        return stockBalanceRepository.findIssuableBalancesFefo(itemId, warehouseIds, LotStatus.AVAILABLE, asOf);
    }

    /** The balance of one item in one warehouse for one lot, or for the lot-less position when {@code lotId} is null. */
    public Optional<StockBalance> findPosition(UUID itemId, UUID warehouseId, UUID lotId) {
        return lotId == null
                ? stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(itemId, warehouseId)
                : stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(itemId, warehouseId, lotId);
    }

    public void reserve(StockBalance balance, BigDecimal quantity) {
        balance.reserve(quantity);
        stockBalanceRepository.save(balance);
    }

    public void releaseReserved(StockBalance balance, BigDecimal quantity) {
        balance.releaseReserved(quantity);
        stockBalanceRepository.save(balance);
    }
}
