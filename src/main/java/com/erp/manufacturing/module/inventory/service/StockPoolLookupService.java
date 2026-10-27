package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.MovementType;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * G11 (D3): the quantities of one item in one warehouse — the "pool" a work order receives its output into. Read by
 * production costing to capture the pool at release and to prove, at close, that nothing but the work order's own
 * receipts, QC splits and deliveries moved it. Quantities only: the warehouse holds no value (D1). No
 * {@code @PreAuthorize}: callers are already authorised on the work order.
 */
@Service
@RequiredArgsConstructor
public class StockPoolLookupService {

    private final StockBalanceRepository stockBalanceRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public BigDecimal onHand(UUID itemId, UUID warehouseId) {
        return stockBalanceRepository.sumOnHand(itemId, warehouseId);
    }

    @Transactional(readOnly = true)
    public List<PoolMovement> movementsSince(UUID itemId, UUID warehouseId, Instant since) {
        return stockMovementRepository.findPoolMovements(itemId, warehouseId, since).stream()
                .map(StockPoolLookupService::toPoolMovement)
                .toList();
    }

    private static PoolMovement toPoolMovement(StockMovement movement) {
        return new PoolMovement(movement.getMovementId(), movement.getMovementType(), movement.getReferenceType(),
                movement.getReferenceId(), movement.getQuantity(),
                movement.getLot() == null ? null : movement.getLot().getLotId(), movement.getCreatedAt());
    }

    public record PoolMovement(UUID movementId, MovementType movementType, String referenceType, String referenceId,
                               BigDecimal quantity, UUID lotId, Instant createdAt) {}
}
