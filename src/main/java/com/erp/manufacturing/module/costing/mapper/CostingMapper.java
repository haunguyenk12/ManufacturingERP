package com.erp.manufacturing.module.costing.mapper;

import com.erp.manufacturing.module.costing.domain.ItemStandardCost;
import com.erp.manufacturing.module.costing.dto.ItemStandardCostResponse;
import com.erp.manufacturing.module.costing.service.StandardCostBreakdown;
import org.springframework.stereotype.Component;

@Component
public class CostingMapper {

    public ItemStandardCostResponse toResponse(ItemStandardCost cost, StandardCostBreakdown breakdown) {
        return new ItemStandardCostResponse(
                cost.getItemStandardCostId(),
                cost.getCompany().getCompanyId(),
                cost.getItem().getItemId(),
                cost.getItem().getCode(),
                cost.getMaterialCost(),
                cost.getLaborCost(),
                cost.getOverheadCost(),
                breakdown.totalCost(),
                cost.getVersion(),
                cost.getCreatedAt(),
                cost.getUpdatedAt());
    }
}
