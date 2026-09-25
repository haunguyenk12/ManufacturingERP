package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.planning.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlanningService {

    private static final int BUILDABLE_SCALE = 6;

    private final ItemLookupService itemLookupService;
    private final BomLookupService bomLookupService;
    private final InventoryAvailabilityService inventoryAvailabilityService;
    private final OrganizationLookupService organizationLookupService;

    @Transactional(readOnly = true)
    @PreAuthorize("@planningPermissionGuard.canEstimate(authentication, #request)")
    public ProductionEstimateResponse estimateProduction(ProductionEstimateRequest request) {
        OrganizationScopeResolution scope = organizationLookupService.resolveScope(request.scopeType(), request.scopeId());
        Item product = itemLookupService.getActiveItem(request.productItemId());
        ensureProductBelongsToScopeCompany(product, scope);

        BomHeader rootBom = bomLookupService.getActiveBom(scope.companyId(), product.getItemId());
        Map<UUID, Requirement> requiredPerUnit = new LinkedHashMap<>();
        explodeBom(rootBom, BigDecimal.ONE, requiredPerUnit, new LinkedHashSet<>());

        Map<UUID, BigDecimal> availableQuantities = inventoryAvailabilityService.getAvailableQuantities(
                requiredPerUnit.keySet(), scope.warehouseIds());

        List<ProductionEstimateLineResponse> lines = buildLines(
                requiredPerUnit, availableQuantities, request.targetQuantity());
        long shortageCount = lines.stream()
                .filter(line -> !line.sufficient())
                .count();

        return new ProductionEstimateResponse(
                product.getItemId(),
                product.getCode(),
                product.getName(),
                scope.scopeType().name(),
                scope.scopeId(),
                scope.companyId(),
                request.targetQuantity(),
                calculateMaxBuildable(requiredPerUnit, availableQuantities),
                lines,
                new ProductionEstimateSummaryResponse(
                        lines.size(),
                        Math.toIntExact(shortageCount),
                        shortageCount == 0));
    }

    private void explodeBom(BomHeader bom,
                            BigDecimal parentRequiredPerUnit,
                            Map<UUID, Requirement> requirements,
                            Set<UUID> path) {
        UUID parentItemId = bom.getParentItem().getItemId();
        if (!path.add(parentItemId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE);
        }

        for (BomLine line : bom.getLines()) {
            Item component = line.getComponentItem();
            UUID componentItemId = component.getItemId();
            if (path.contains(componentItemId)) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE);
            }

            BigDecimal componentRequiredPerUnit = parentRequiredPerUnit
                    .multiply(line.getQuantityPer())
                    .multiply(BigDecimal.ONE.add(line.getScrapRate()));

            Optional<BomHeader> childBom = canHaveBom(component)
                    ? bomLookupService.findActiveBom(bom.getCompany().getCompanyId(), componentItemId)
                    : Optional.empty();

            if (childBom.isPresent()) {
                explodeBom(childBom.get(), componentRequiredPerUnit, requirements, path);
            } else {
                requirements.merge(
                        componentItemId,
                        new Requirement(component, componentRequiredPerUnit),
                        (existing, added) -> new Requirement(
                                existing.item(),
                                existing.requiredPerUnit().add(added.requiredPerUnit())));
            }
        }

        path.remove(parentItemId);
    }

    private List<ProductionEstimateLineResponse> buildLines(Map<UUID, Requirement> requiredPerUnit,
                                                            Map<UUID, BigDecimal> availableQuantities,
                                                            BigDecimal targetQuantity) {
        return requiredPerUnit.values().stream()
                .sorted(Comparator.comparing(requirement -> requirement.item().getCode()))
                .map(requirement -> {
                    BigDecimal requiredQuantity = requirement.requiredPerUnit().multiply(targetQuantity);
                    BigDecimal availableQuantity = availableQuantities.getOrDefault(
                            requirement.item().getItemId(), BigDecimal.ZERO);
                    BigDecimal shortageQuantity = positiveDifference(requiredQuantity, availableQuantity);
                    return new ProductionEstimateLineResponse(
                            requirement.item().getItemId(),
                            requirement.item().getCode(),
                            requirement.item().getName(),
                            requiredQuantity,
                            availableQuantity,
                            shortageQuantity,
                            shortageQuantity.compareTo(BigDecimal.ZERO) == 0);
                })
                .toList();
    }

    private BigDecimal calculateMaxBuildable(Map<UUID, Requirement> requiredPerUnit,
                                             Map<UUID, BigDecimal> availableQuantities) {
        if (requiredPerUnit.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal maxBuildable = null;
        for (Requirement requirement : requiredPerUnit.values()) {
            BigDecimal availableQuantity = availableQuantities.getOrDefault(
                    requirement.item().getItemId(), BigDecimal.ZERO);
            BigDecimal componentBuildable = availableQuantity.divide(
                    requirement.requiredPerUnit(), BUILDABLE_SCALE, RoundingMode.DOWN);
            maxBuildable = maxBuildable == null || componentBuildable.compareTo(maxBuildable) < 0
                    ? componentBuildable
                    : maxBuildable;
        }
        return maxBuildable == null ? BigDecimal.ZERO : maxBuildable;
    }

    private boolean canHaveBom(Item item) {
        return item.getType() == ItemType.WIP || item.getType() == ItemType.FINISHED_GOOD;
    }

    private void ensureProductBelongsToScopeCompany(Item product, OrganizationScopeResolution scope) {
        if (!product.getCompany().getCompanyId().equals(scope.companyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Product item must belong to the requested planning scope company");
        }
    }

    private BigDecimal positiveDifference(BigDecimal requiredQuantity, BigDecimal availableQuantity) {
        BigDecimal difference = requiredQuantity.subtract(availableQuantity);
        return difference.compareTo(BigDecimal.ZERO) > 0 ? difference : BigDecimal.ZERO;
    }

    private record Requirement(Item item, BigDecimal requiredPerUnit) {}
}
