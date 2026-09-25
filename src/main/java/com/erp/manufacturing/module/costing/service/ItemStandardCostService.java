package com.erp.manufacturing.module.costing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.costing.domain.ItemStandardCost;
import com.erp.manufacturing.module.costing.dto.ItemStandardCostRequest;
import com.erp.manufacturing.module.costing.dto.ItemStandardCostResponse;
import com.erp.manufacturing.module.costing.mapper.CostingMapper;
import com.erp.manufacturing.module.costing.repository.ItemStandardCostRepository;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemStandardCostService {

    private final ItemStandardCostRepository itemStandardCostRepository;
    private final ItemLookupService itemLookupService;
    private final CostingService costingService;
    private final CostingMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_COSTING_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.ITEM_STANDARD_COST_UPSERTED, entityType = "ItemStandardCost",
            entityIdExpression = "itemStandardCostId.toString()",
               companyId = "#result?.companyId()")
    public ItemStandardCostResponse upsert(UUID companyId, UUID itemId, ItemStandardCostRequest request) {
        Item item = itemLookupService.getActiveItem(itemId);
        ensureItemBelongsToCompany(item, companyId);

        Optional<ItemStandardCost> existing = itemStandardCostRepository.findByItemItemId(itemId);
        ItemStandardCost cost = existing.orElseGet(() -> ItemStandardCost.builder()
                .company(item.getCompany())
                .item(item)
                .build());
        cost.setMaterialCost(request.materialCost());
        cost.setLaborCost(request.laborCost());
        cost.setOverheadCost(request.overheadCost());

        return toResponse(companyId, itemStandardCostRepository.save(cost));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_COSTING_READ', 'COMPANY', #companyId)")
    public ItemStandardCostResponse get(UUID companyId, UUID itemId) {
        Item item = itemLookupService.getActiveItem(itemId);
        ensureItemBelongsToCompany(item, companyId);
        ItemStandardCost cost = itemStandardCostRepository.findByItemItemId(itemId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Item standard cost", itemId));
        return toResponse(companyId, cost);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_COSTING_READ', 'COMPANY', #companyId)")
    public PageResult<ItemStandardCostResponse> list(UUID companyId, UUID itemId, Pageable pageable) {
        return PageResult.from(itemStandardCostRepository.search(companyId, itemId, pageable)
                .map(cost -> toResponse(companyId, cost)));
    }

    private ItemStandardCostResponse toResponse(UUID companyId, ItemStandardCost cost) {
        StandardCostBreakdown breakdown = costingService.calculateStandardCost(companyId, cost.getItem().getItemId());
        return mapper.toResponse(cost, breakdown);
    }

    private void ensureItemBelongsToCompany(Item item, UUID companyId) {
        if (!item.getCompany().getCompanyId().equals(companyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Item must belong to the given company");
        }
    }
}
