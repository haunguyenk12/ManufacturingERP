package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService.PlanningInventoryQuantity;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderSupplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MrpCalculationService {

    private final BomLookupService bomLookupService;
    private final InventoryAvailabilityService inventoryAvailabilityService;
    private final WorkOrderSupplyService workOrderSupplyService;

    public MrpCalculationResult calculate(MrpRun run,
                                          List<PlanningDemand> demands,
                                          Collection<UUID> scopeWarehouseIds) {
        if (demands.isEmpty()) {
            return new MrpCalculationResult(List.of(), List.of());
        }

        List<RequirementDraft> allRequirements = new ArrayList<>();
        List<SuggestionDraft> allSuggestions = new ArrayList<>();
        List<RequirementSeed> currentLevel = demands.stream()
                .map(demand -> new RequirementSeed(
                        null,
                        demand,
                        demand.getItem(),
                        demand.getWarehouse(),
                        demand.getRequiredQuantity(),
                        demand.getDueDate(),
                        0,
                        new LinkedHashSet<>(Set.of(demand.getItem().getItemId()))))
                .toList();

        Map<ItemScopeKey, BigDecimal> consumedCoverageByItem = new HashMap<>();
        while (!currentLevel.isEmpty()) {
            Set<UUID> manufacturableItemIds = currentLevel.stream()
                    .map(RequirementSeed::item)
                    .filter(this::canHaveBom)
                    .map(Item::getItemId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<UUID, BomHeader> activeBoms = bomLookupService.findActiveBoms(
                    run.getCompany().getCompanyId(), manufacturableItemIds);
            LevelSupplySnapshot supplySnapshot = loadLevelSupplySnapshot(run, currentLevel, scopeWarehouseIds);

            List<RequirementSeed> nextLevel = new ArrayList<>();
            for (RequirementSeed seed : currentLevel) {
                ItemScopeKey itemScopeKey = itemScopeKey(seed);
                PlanningInventoryQuantity inventory = supplySnapshot.inventoryBySeed()
                        .getOrDefault(seed, PlanningInventoryQuantity.empty());
                BigDecimal openSupplyQuantity = supplySnapshot.openSupplyBySeed()
                        .getOrDefault(seed, BigDecimal.ZERO);

                BigDecimal stockTargetQuantity = max(
                        inventory.safetyStockQuantity(),
                        inventory.reorderPointQuantity());
                BigDecimal remainingCoverage = positiveDifference(
                        inventory.availableQuantity().add(openSupplyQuantity),
                        consumedCoverageByItem.getOrDefault(itemScopeKey, BigDecimal.ZERO));
                BigDecimal netRequiredQuantity = positiveDifference(
                        seed.grossRequiredQuantity().add(stockTargetQuantity),
                        remainingCoverage);
                BigDecimal consumedCoverage = seed.grossRequiredQuantity().add(stockTargetQuantity)
                        .min(remainingCoverage);
                consumedCoverageByItem.merge(itemScopeKey, consumedCoverage, BigDecimal::add);

                BomHeader activeBom = activeBoms.get(seed.item().getItemId());
                MrpRequirementStatus status = determineStatus(seed.item(), activeBom, netRequiredQuantity);
                LocalDate suggestedOrderDate = seed.dueDate().minusDays(inventory.leadTimeDays());
                RequirementDraft requirement = new RequirementDraft(
                        seed.parent(),
                        seed.sourceDemand(),
                        seed.item(),
                        seed.warehouse(),
                        seed.level(),
                        seed.grossRequiredQuantity(),
                        inventory.availableQuantity(),
                        inventory.reservedQuantity(),
                        openSupplyQuantity,
                        stockTargetQuantity,
                        netRequiredQuantity,
                        seed.dueDate(),
                        status,
                        noteFor(status, seed.item()),
                        suggestedOrderDate);
                allRequirements.add(requirement);

                if (netRequiredQuantity.compareTo(BigDecimal.ZERO) > 0 && status == MrpRequirementStatus.SHORTAGE) {
                    SupplySuggestionType suggestionType = activeBom == null
                            ? SupplySuggestionType.PURCHASE_REQUISITION
                            : SupplySuggestionType.WORK_ORDER;
                    allSuggestions.add(new SuggestionDraft(requirement, suggestionType));
                }

                if (netRequiredQuantity.compareTo(BigDecimal.ZERO) > 0 && activeBom != null) {
                    nextLevel.addAll(expandChildren(requirement, seed, activeBom, netRequiredQuantity, suggestedOrderDate));
                }
            }
            currentLevel = nextLevel;
        }

        return new MrpCalculationResult(allRequirements, allSuggestions);
    }

    private LevelSupplySnapshot loadLevelSupplySnapshot(MrpRun run,
                                                        List<RequirementSeed> seeds,
                                                        Collection<UUID> scopeWarehouseIds) {
        Map<RequirementSeed, PlanningInventoryQuantity> inventoryBySeed = new IdentityHashMap<>();
        Map<RequirementSeed, BigDecimal> openSupplyBySeed = new IdentityHashMap<>();
        Map<WarehouseScopeKey, List<RequirementSeed>> seedsByWarehouseScope = seeds.stream()
                .collect(Collectors.groupingBy(seed -> new WarehouseScopeKey(
                        seed.warehouse() == null ? null : seed.warehouse().getWarehouseId())));

        for (Map.Entry<WarehouseScopeKey, List<RequirementSeed>> entry : seedsByWarehouseScope.entrySet()) {
            Collection<UUID> warehouseIds = entry.getKey().warehouseId() == null
                    ? scopeWarehouseIds
                    : List.of(entry.getKey().warehouseId());
            Set<UUID> itemIds = entry.getValue().stream()
                    .map(seed -> seed.item().getItemId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<UUID, PlanningInventoryQuantity> inventoryByItem = inventoryAvailabilityService
                    .getPlanningQuantities(itemIds, warehouseIds);
            Map<UUID, BigDecimal> openSupplyByItem = workOrderSupplyService.getOpenSupplyQuantities(
                    run.getCompany().getCompanyId(),
                    run.getPlant().getPlantId(),
                    warehouseIds,
                    itemIds);
            for (RequirementSeed seed : entry.getValue()) {
                UUID itemId = seed.item().getItemId();
                inventoryBySeed.put(seed, inventoryByItem.getOrDefault(itemId, PlanningInventoryQuantity.empty()));
                openSupplyBySeed.put(seed, openSupplyByItem.getOrDefault(itemId, BigDecimal.ZERO));
            }
        }

        return new LevelSupplySnapshot(inventoryBySeed, openSupplyBySeed);
    }

    private List<RequirementSeed> expandChildren(RequirementDraft parent,
                                                 RequirementSeed seed,
                                                 BomHeader activeBom,
                                                 BigDecimal parentNetRequiredQuantity,
                                                 LocalDate childDueDate) {
        List<RequirementSeed> children = new ArrayList<>();
        for (BomLine line : activeBom.getLines()) {
            Item component = line.getComponentItem();
            if (seed.path().contains(component.getItemId())) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE);
            }
            LinkedHashSet<UUID> childPath = new LinkedHashSet<>(seed.path());
            childPath.add(component.getItemId());
            BigDecimal grossRequiredQuantity = parentNetRequiredQuantity
                    .multiply(line.getQuantityPer())
                    .multiply(BigDecimal.ONE.add(line.getScrapRate()));
            children.add(new RequirementSeed(
                    parent,
                    seed.sourceDemand(),
                    component,
                    seed.warehouse(),
                    grossRequiredQuantity,
                    childDueDate,
                    seed.level() + 1,
                    childPath));
        }
        return children;
    }

    private Collection<UUID> effectiveWarehouseIds(Warehouse warehouse, Collection<UUID> scopeWarehouseIds) {
        if (warehouse != null) {
            return List.of(warehouse.getWarehouseId());
        }
        return scopeWarehouseIds;
    }

    private ItemScopeKey itemScopeKey(RequirementSeed seed) {
        return new ItemScopeKey(seed.item().getItemId(), seed.warehouse() == null ? null : seed.warehouse().getWarehouseId());
    }

    private MrpRequirementStatus determineStatus(Item item, BomHeader activeBom, BigDecimal netRequiredQuantity) {
        if (netRequiredQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return MrpRequirementStatus.COVERED;
        }
        if (canHaveBom(item) && activeBom == null) {
            return MrpRequirementStatus.BOM_MISSING;
        }
        return MrpRequirementStatus.SHORTAGE;
    }

    private String noteFor(MrpRequirementStatus status, Item item) {
        if (status == MrpRequirementStatus.BOM_MISSING) {
            return "No active BOM found for manufacturable item " + item.getCode();
        }
        return null;
    }

    private boolean canHaveBom(Item item) {
        return item.getType() == ItemType.WIP || item.getType() == ItemType.FINISHED_GOOD;
    }

    private BigDecimal positiveDifference(BigDecimal left, BigDecimal right) {
        BigDecimal difference = left.subtract(right);
        return difference.compareTo(BigDecimal.ZERO) > 0 ? difference : BigDecimal.ZERO;
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    public record MrpCalculationResult(
            List<RequirementDraft> requirements,
            List<SuggestionDraft> suggestions
    ) {}

    public record RequirementDraft(
            RequirementDraft parent,
            PlanningDemand sourceDemand,
            Item item,
            Warehouse warehouse,
            int level,
            BigDecimal grossRequiredQuantity,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            BigDecimal openSupplyQuantity,
            BigDecimal safetyStockQuantity,
            BigDecimal netRequiredQuantity,
            LocalDate dueDate,
            MrpRequirementStatus status,
            String note,
            LocalDate suggestedOrderDate
    ) {}

    public record SuggestionDraft(
            RequirementDraft requirement,
            SupplySuggestionType suggestionType
    ) {}

    private record RequirementSeed(
            RequirementDraft parent,
            PlanningDemand sourceDemand,
            Item item,
            Warehouse warehouse,
            BigDecimal grossRequiredQuantity,
            LocalDate dueDate,
            int level,
            LinkedHashSet<UUID> path
    ) {}

    private record WarehouseScopeKey(UUID warehouseId) {}

    private record ItemScopeKey(UUID itemId, UUID warehouseId) {}

    private record LevelSupplySnapshot(
            Map<RequirementSeed, PlanningInventoryQuantity> inventoryBySeed,
            Map<RequirementSeed, BigDecimal> openSupplyBySeed
    ) {}
}
