package com.erp.manufacturing.module.costing.service;

import com.erp.manufacturing.module.costing.repository.ItemStandardCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only entry point for other modules to consume costing data (rule C7) — {@code workorder}
 * must go through this, not {@link ItemStandardCostRepository} or {@link CostingService} directly.
 */
@Service
@RequiredArgsConstructor
public class ItemStandardCostLookupService {

    private final CostingService costingService;
    private final ItemStandardCostRepository itemStandardCostRepository;

    /**
     * The fully-loaded standard cost of one unit of {@code itemId} — material + labor + overhead,
     * rolled up through the BOM if the item is itself manufactured. This is what "consuming one
     * unit of this item" costs, whether it is a purchased raw material or a sub-assembly.
     */
    @Transactional(readOnly = true)
    public BigDecimal findStandardUnitCost(UUID companyId, UUID itemId) {
        return costingService.calculateStandardCost(companyId, itemId).totalCost();
    }

    @Transactional(readOnly = true)
    public StandardCostBreakdown findStandardCostBreakdown(UUID companyId, UUID itemId) {
        return costingService.calculateStandardCost(companyId, itemId);
    }

    /**
     * The item's own labor/overhead rate — flat, manually entered, never rolled up from its BOM
     * (P3 decision #1). Used only for the work order's own product item, never for components.
     */
    @Transactional(readOnly = true)
    public LaborOverheadCost findLaborOverheadCost(UUID itemId) {
        return itemStandardCostRepository.findByItemItemId(itemId)
                .map(cost -> new LaborOverheadCost(cost.getLaborCost(), cost.getOverheadCost()))
                .orElse(LaborOverheadCost.ZERO);
    }

    public record LaborOverheadCost(BigDecimal laborCost, BigDecimal overheadCost) {
        public static final LaborOverheadCost ZERO = new LaborOverheadCost(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
