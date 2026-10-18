package com.erp.manufacturing.module.purchasing.service.query;

import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderSupplyProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Open purchase order supply for MRP netting (spec §2.1 {@code scheduledReceipts}).
 *
 * <p>Cross-module entry point for {@code planning} (rule {@code C7}) — the mirror of
 * {@code WorkOrderSupplyService}. Together the two make up the whole {@code scheduledReceipts}
 * term; before this existed MRP proposed buying what was already on order (debt #16).
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderSupplyService {

    /**
     * Only confirmed orders count as goods that will arrive. {@code DRAFT} does not: nothing has
     * been promised yet. {@code FULLY_RECEIVED}/{@code CLOSED}/{@code CANCELLED} have no open
     * quantity left to expect (G7a renamed {@code SENT} to {@code CONFIRMED}).
     */
    private static final List<PurchaseOrderStatus> OPEN_SUPPLY_STATUSES = List.of(
            PurchaseOrderStatus.CONFIRMED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getOpenSupplyQuantities(UUID companyId,
                                                         UUID plantId,
                                                         Collection<UUID> warehouseIds,
                                                         Collection<UUID> itemIds) {
        if (companyId == null || plantId == null || warehouseIds == null || warehouseIds.isEmpty()
                || itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        return purchaseOrderRepository.aggregateOpenSupply(
                        companyId, plantId, warehouseIds, itemIds, OPEN_SUPPLY_STATUSES)
                .stream()
                .collect(Collectors.toMap(
                        PurchaseOrderSupplyProjection::getItemId,
                        PurchaseOrderSupplyProjection::getOpenSupplyQuantity));
    }
}
