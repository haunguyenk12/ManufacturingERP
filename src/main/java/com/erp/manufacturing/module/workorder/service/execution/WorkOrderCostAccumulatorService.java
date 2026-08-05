package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.module.costing.service.ItemStandardCostLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCostAccumulator;
import com.erp.manufacturing.module.workorder.repository.WorkOrderCostAccumulatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Accumulates the actual cost a work order has incurred so far. Both entry points find-or-create
 * the accumulator row and add to it — never subtract, never reset (no reversal path exists yet for
 * either material issue or production execution).
 */
@Service
@RequiredArgsConstructor
public class WorkOrderCostAccumulatorService {

    private final WorkOrderCostAccumulatorRepository accumulatorRepository;
    private final ItemStandardCostLookupService itemStandardCostLookupService;

    @Transactional
    public void accumulateMaterialCost(WorkOrder workOrder, Item componentItem, BigDecimal quantity) {
        BigDecimal unitCost = itemStandardCostLookupService.findStandardUnitCost(
                workOrder.getCompany().getCompanyId(), componentItem.getItemId());
        findOrCreate(workOrder).addMaterialCost(quantity.multiply(unitCost));
    }

    @Transactional
    public void accumulateLaborOverheadCost(WorkOrder workOrder, BigDecimal goodQuantity) {
        ItemStandardCostLookupService.LaborOverheadCost rate =
                itemStandardCostLookupService.findLaborOverheadCost(workOrder.getProductItem().getItemId());
        findOrCreate(workOrder).addLaborAndOverheadCost(
                goodQuantity.multiply(rate.laborCost()),
                goodQuantity.multiply(rate.overheadCost()));
    }

    /**
     * The returned entity is managed within the caller's transaction — subsequent mutation via
     * {@code addMaterialCost}/{@code addLaborAndOverheadCost} is flushed by dirty checking, no
     * explicit {@code save()} needed after this (same pattern as
     * {@code WorkOrderComponentLine.addIssuedQuantity} elsewhere in this module).
     */
    private WorkOrderCostAccumulator findOrCreate(WorkOrder workOrder) {
        return accumulatorRepository.findByWorkOrderWorkOrderId(workOrder.getWorkOrderId())
                .orElseGet(() -> accumulatorRepository.save(
                        WorkOrderCostAccumulator.builder().workOrder(workOrder).build()));
    }
}
