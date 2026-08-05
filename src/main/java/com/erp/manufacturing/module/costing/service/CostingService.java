package com.erp.manufacturing.module.costing.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.costing.domain.ItemStandardCost;
import com.erp.manufacturing.module.costing.repository.ItemStandardCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Recursive standard-cost roll-up over the BOM tree — the costing analogue of
 * {@code MrpCalculationService.expandChildren}.
 */
@Service
@RequiredArgsConstructor
public class CostingService {

    private final ItemStandardCostRepository itemStandardCostRepository;
    private final BomLookupService bomLookupService;

    @Transactional(readOnly = true)
    public StandardCostBreakdown calculateStandardCost(UUID companyId, UUID itemId) {
        return calculate(companyId, itemId, new LinkedHashSet<>());
    }

    /**
     * @param path items already visited on this branch of the recursion (defense-in-depth — B8
     *             should already keep an {@code ACTIVE} BOM acyclic, this is a second line of
     *             defense the same way {@code MrpCalculationService.expandChildren} keeps one)
     */
    private StandardCostBreakdown calculate(UUID companyId, UUID itemId, LinkedHashSet<UUID> path) {
        if (path.contains(itemId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE);
        }
        LinkedHashSet<UUID> nextPath = new LinkedHashSet<>(path);
        nextPath.add(itemId);

        Optional<ItemStandardCost> ownCost = itemStandardCostRepository.findByItemItemId(itemId);
        BigDecimal laborCost = ownCost.map(ItemStandardCost::getLaborCost).orElse(BigDecimal.ZERO);
        BigDecimal overheadCost = ownCost.map(ItemStandardCost::getOverheadCost).orElse(BigDecimal.ZERO);

        Optional<BomHeader> activeBom = bomLookupService.findActiveBom(companyId, itemId);
        BigDecimal materialCost;
        if (activeBom.isPresent()) {
            materialCost = BigDecimal.ZERO;
            for (BomLine line : activeBom.get().getLines()) {
                UUID componentItemId = line.getComponentItem().getItemId();
                BigDecimal componentUnitCost = calculate(companyId, componentItemId, nextPath).totalCost();
                BigDecimal contribution = componentUnitCost
                        .multiply(line.getQuantityPer())
                        .multiply(BigDecimal.ONE.add(line.getScrapRate()));
                materialCost = materialCost.add(contribution);
            }
        } else {
            materialCost = ownCost.map(ItemStandardCost::getMaterialCost).orElse(BigDecimal.ZERO);
        }
        return new StandardCostBreakdown(materialCost, laborCost, overheadCost);
    }
}
