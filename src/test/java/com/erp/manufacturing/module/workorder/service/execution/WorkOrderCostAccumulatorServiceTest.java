package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.module.costing.service.ItemStandardCostLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCostAccumulator;
import com.erp.manufacturing.module.workorder.repository.WorkOrderCostAccumulatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderCostAccumulatorService tests")
class WorkOrderCostAccumulatorServiceTest {

    @Mock WorkOrderCostAccumulatorRepository accumulatorRepository;
    @Mock ItemStandardCostLookupService itemStandardCostLookupService;

    WorkOrderCostAccumulatorService service;

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID WORK_ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WorkOrderCostAccumulatorService(accumulatorRepository, itemStandardCostLookupService);
        // Only used by tests where no accumulator row exists yet (findOrCreate must save the new
        // row); lenient because the "existing accumulator" tests mutate a managed entity by dirty
        // checking instead and never call save() themselves.
        lenient().when(accumulatorRepository.save(any(WorkOrderCostAccumulator.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Company company() {
        return Company.builder().companyId(COMPANY_ID).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
    }

    private Item item(UUID itemId, ItemType type) {
        return Item.builder().itemId(itemId).company(company()).code("ITEM").name("Item")
                .type(type).unit("EA").status(ItemStatus.ACTIVE).build();
    }

    private WorkOrder workOrder(Item productItem) {
        return WorkOrder.builder()
                .workOrderId(WORK_ORDER_ID).company(company()).productItem(productItem)
                .plannedQuantity(new BigDecimal("10")).build();
    }

    @Test
    @DisplayName("accumulateMaterialCost: no existing accumulator creates one and adds quantity * standardUnitCost")
    void accumulateMaterialCost_noExistingAccumulator_createsAndAdds() {
        Item component = item(UUID.randomUUID(), ItemType.RAW_MATERIAL);
        WorkOrder workOrder = workOrder(item(UUID.randomUUID(), ItemType.FINISHED_GOOD));
        when(accumulatorRepository.findByWorkOrderWorkOrderId(WORK_ORDER_ID)).thenReturn(Optional.empty());
        when(itemStandardCostLookupService.findStandardUnitCost(COMPANY_ID, component.getItemId()))
                .thenReturn(new BigDecimal("4"));

        service.accumulateMaterialCost(workOrder, component, new BigDecimal("3"));

        var captor = org.mockito.ArgumentCaptor.forClass(WorkOrderCostAccumulator.class);
        org.mockito.Mockito.verify(accumulatorRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getMaterialCostAccumulated()).isEqualByComparingTo("12"); // 3 * 4
    }

    @Test
    @DisplayName("accumulateMaterialCost: existing accumulator adds to the running total, does not replace it")
    void accumulateMaterialCost_existingAccumulator_addsToRunningTotal() {
        Item component = item(UUID.randomUUID(), ItemType.RAW_MATERIAL);
        WorkOrder workOrder = workOrder(item(UUID.randomUUID(), ItemType.FINISHED_GOOD));
        WorkOrderCostAccumulator existing = WorkOrderCostAccumulator.builder()
                .workOrderCostAccumulatorId(UUID.randomUUID()).workOrder(workOrder)
                .materialCostAccumulated(new BigDecimal("10")).build();
        when(accumulatorRepository.findByWorkOrderWorkOrderId(WORK_ORDER_ID)).thenReturn(Optional.of(existing));
        when(itemStandardCostLookupService.findStandardUnitCost(COMPANY_ID, component.getItemId()))
                .thenReturn(new BigDecimal("2"));

        service.accumulateMaterialCost(workOrder, component, new BigDecimal("5"));

        assertThat(existing.getMaterialCostAccumulated()).isEqualByComparingTo("20"); // 10 + 5*2
    }

    @Test
    @DisplayName("accumulateLaborOverheadCost: adds goodQuantity * (laborCost + overheadCost) of the PRODUCT item, not a component")
    void accumulateLaborOverheadCost_usesProductItemsFlatRate() {
        Item productItem = item(UUID.randomUUID(), ItemType.FINISHED_GOOD);
        WorkOrder workOrder = workOrder(productItem);
        when(accumulatorRepository.findByWorkOrderWorkOrderId(WORK_ORDER_ID)).thenReturn(Optional.empty());
        when(itemStandardCostLookupService.findLaborOverheadCost(productItem.getItemId()))
                .thenReturn(new ItemStandardCostLookupService.LaborOverheadCost(new BigDecimal("3"), new BigDecimal("1")));

        service.accumulateLaborOverheadCost(workOrder, new BigDecimal("4"));

        var captor = org.mockito.ArgumentCaptor.forClass(WorkOrderCostAccumulator.class);
        org.mockito.Mockito.verify(accumulatorRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getLaborCostAccumulated()).isEqualByComparingTo("12");    // 4 * 3
        assertThat(captor.getValue().getOverheadCostAccumulated()).isEqualByComparingTo("4");  // 4 * 1
    }

    @Test
    @DisplayName("no ItemStandardCost configured for the component contributes ZERO without throwing")
    void accumulateMaterialCost_noStandardCostConfigured_contributesZero() {
        Item component = item(UUID.randomUUID(), ItemType.RAW_MATERIAL);
        WorkOrder workOrder = workOrder(item(UUID.randomUUID(), ItemType.FINISHED_GOOD));
        when(accumulatorRepository.findByWorkOrderWorkOrderId(WORK_ORDER_ID)).thenReturn(Optional.empty());
        when(itemStandardCostLookupService.findStandardUnitCost(COMPANY_ID, component.getItemId()))
                .thenReturn(BigDecimal.ZERO);

        service.accumulateMaterialCost(workOrder, component, new BigDecimal("100"));

        var captor = org.mockito.ArgumentCaptor.forClass(WorkOrderCostAccumulator.class);
        org.mockito.Mockito.verify(accumulatorRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getMaterialCostAccumulated()).isEqualByComparingTo("0");
    }
}
