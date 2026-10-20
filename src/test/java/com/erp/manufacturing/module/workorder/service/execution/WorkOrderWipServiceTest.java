package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.wip.WipSection;
import com.erp.manufacturing.module.workorder.dto.wip.WipWorkOrderSummaryResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WorkOrderWipResponse;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnLineRepository;
import com.erp.manufacturing.module.workorder.repository.OpenReceiptQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionConsumptionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationInputRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderReworkTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.erp.manufacturing.module.workorder.service.execution.ProductionExecutionServiceTest.keyed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderWipServiceTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock ProductionExecutionRepository executionRepository;
    @Mock ProductionReceiptRepository receiptRepository;
    @Mock WorkOrderOperationRepository operationRepository;
    @Mock WorkOrderOperationInputRepository inputRepository;
    @Mock MaterialIssueLineRepository issueLineRepository;
    @Mock ProductionExecutionConsumptionRepository consumptionRepository;
    @Mock MaterialReturnLineRepository returnLineRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;

    private WorkOrderWipService service;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        service = new WorkOrderWipService(workOrderRepository, executionRepository, receiptRepository,
                new OperationLedger(operationRepository, inputRepository, executionRepository),
                new MaterialSourceLedger(issueLineRepository, consumptionRepository, returnLineRepository),
                new WorkOrderExecutionSupport(workOrderRepository, organizationLookupService, inventoryAvailabilityService));
        Company company = Company.builder().companyId(UUID.randomUUID()).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P1").name("Plant 1")
                .status(OrganizationStatus.ACTIVE).build();
        Item product = Item.builder().itemId(UUID.randomUUID()).company(company).code("FG-1").name("Frame")
                .type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).build();
        workOrder = WorkOrder.builder().workOrderId(UUID.randomUUID()).company(company).plant(plant).workOrderNo("WO-1")
                .productItem(product).plannedQuantity(BigDecimal.TEN).completedQuantity(new BigDecimal("2"))
                .actualGoodQuantity(new BigDecimal("9")).actualScrapQuantity(BigDecimal.ONE)
                .actualReworkQuantity(new BigDecimal("3")).terminalShortfallQuantity(BigDecimal.ONE)
                .status(WorkOrderStatus.COMPLETED).componentLines(new ArrayList<>()).operations(new ArrayList<>()).build();
        workOrder.setVersion(13L);
    }

    /**
     * Output awaiting receipt: final good 9, 2 already warehoused, 3 claimed by open receipts ⇒ 7 still receivable (open
     * receipts are reported, not deducted — B16). A work order with nothing asked for gets every section.
     */
    @Test
    void get_withoutASection_returnsEverySectionAndTheOutputAwaitingReceipt() {
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(receiptRepository.sumOpenQuantityByWorkOrderIds(List.of(workOrder.getWorkOrderId())))
                .thenReturn(List.of(open(workOrder.getWorkOrderId(), "3")));

        WorkOrderWipResponse wip = service.get(workOrder.getWorkOrderId(), null);

        assertThat(wip.version()).isEqualTo(13L);
        assertThat(wip.materials()).isEmpty();
        assertThat(wip.operations()).isEmpty();
        assertThat(wip.rework()).isEmpty();
        assertThat(wip.output().uom()).isEqualTo("EA");
        assertThat(wip.output().finalGoodQuantity()).isEqualByComparingTo("9");
        assertThat(wip.output().approvedReceiptQuantity()).isEqualByComparingTo("2");
        assertThat(wip.output().openReceiptQuantity()).isEqualByComparingTo("3");
        assertThat(wip.output().availableToReceipt()).isEqualByComparingTo("7");
        assertThat(wip.output().terminalShortfallQuantity()).isEqualByComparingTo("1");
    }

    /** One section asked for: the others are null, and the ledgers behind them are not read at all. */
    @Test
    void get_oneSection_leavesTheOthersNullAndUnread() {
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        WorkOrderWipResponse wip = service.get(workOrder.getWorkOrderId(), WipSection.MATERIALS);

        assertThat(wip.section()).isEqualTo(WipSection.MATERIALS);
        assertThat(wip.materials()).isEmpty();
        assertThat(wip.operations()).isNull();
        assertThat(wip.rework()).isNull();
        assertThat(wip.output()).isNull();
        verifyNoInteractions(receiptRepository, operationRepository, executionRepository);
    }

    /** Rework still pending: an execution with rework 2 of which a retry processed 1 shows 1; a fully retried one is gone. */
    @Test
    void get_reworkSection_listsOnlyExecutionsWithReworkStillPending() {
        WorkOrderOperation cut = WorkOrderOperation.builder().workOrderOperationId(UUID.randomUUID()).workOrder(workOrder)
                .sequence(10).name("Cut").build();
        ProductionExecution pending = execution(cut, "2");
        ProductionExecution retried = execution(cut, "1");
        ProductionExecution clean = execution(cut, "0");
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(executionRepository.findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrder.getWorkOrderId()))
                .thenReturn(List.of(pending, retried, clean));
        when(executionRepository.sumRetryProcessedBySource(anyCollection())).thenReturn(List.of(
                keyed(pending.getProductionExecutionId(), "1"), keyed(retried.getProductionExecutionId(), "1")));

        WorkOrderWipResponse wip = service.get(workOrder.getWorkOrderId(), WipSection.REWORK);

        assertThat(wip.rework()).hasSize(1);
        assertThat(wip.rework().get(0).productionExecutionId()).isEqualTo(pending.getProductionExecutionId());
        assertThat(wip.rework().get(0).operationName()).isEqualTo("Cut");
        assertThat(wip.rework().get(0).retryUsedQuantity()).isEqualByComparingTo("1");
        assertThat(wip.rework().get(0).pendingReworkQuantity()).isEqualByComparingTo("1");
        ArgumentCaptor<Collection<UUID>> sources = ArgumentCaptor.forClass(Collection.class);
        verify(executionRepository).sumRetryProcessedBySource(sources.capture());
        assertThat(sources.getValue()).doesNotContain(clean.getProductionExecutionId());
    }

    /** FE Q22: a work order reported before G8c has no known final good — null, never 0. */
    @Test
    void get_onAWorkOrderWithoutInputEvidence_reportsFinalGoodAndTheReceiptAllowanceAsUnknown() {
        workOrder.setOperationTracking(false);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        WorkOrderWipResponse wip = service.get(workOrder.getWorkOrderId(), WipSection.AWAITING_RECEIPT);

        assertThat(wip.inputKnown()).isFalse();
        assertThat(wip.consumptionKnown()).isFalse();
        assertThat(wip.output().finalGoodQuantity()).isNull();
        assertThat(wip.output().availableToReceipt()).isNull();
        assertThat(wip.output().openReceiptQuantity()).isEqualByComparingTo("0");
    }

    /**
     * Without a status filter the list asks for RELEASED, IN_PROGRESS and COMPLETED; pending rework of a row is the
     * rework recorded minus what retries processed, in one query for the page.
     */
    @Test
    void listByPlant_defaultStatusesAndPendingReworkPerRow() {
        UUID plantId = workOrder.getPlant().getPlantId();
        when(workOrderRepository.searchWip(eq(plantId), any(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(workOrder)));
        when(executionRepository.sumReworkByWorkOrderIds(List.of(workOrder.getWorkOrderId())))
                .thenReturn(List.of(reworkTotals(workOrder.getWorkOrderId(), "3", "2")));

        PageResult<WipWorkOrderSummaryResponse> page = service.listByPlant(plantId, null, null, null, null, null,
                PageRequest.of(0, 20));

        ArgumentCaptor<Collection<WorkOrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(workOrderRepository).searchWip(eq(plantId), statuses.capture(), isNull(), isNull(), isNull(), isNull(), any());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED);
        WipWorkOrderSummaryResponse row = page.content().get(0);
        assertThat(row.pendingReworkQuantity()).isEqualByComparingTo("1");
        assertThat(row.historicalReworkQuantity()).isEqualByComparingTo("3");
        assertThat(row.finalGoodQuantity()).isEqualByComparingTo("9");
        assertThat(row.availableToReceipt()).isEqualByComparingTo("7");
        assertThat(row.productItemSku()).isEqualTo("FG-1");
    }

    @Test
    void listByPlant_statusFilterNarrowsToThatStatus() {
        UUID plantId = workOrder.getPlant().getPlantId();
        when(workOrderRepository.searchWip(eq(plantId), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listByPlant(plantId, WorkOrderStatus.COMPLETED, null, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<Collection<WorkOrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(workOrderRepository).searchWip(eq(plantId), statuses.capture(), any(), any(), any(), any(), any());
        assertThat(statuses.getValue()).containsExactly(WorkOrderStatus.COMPLETED);
        verifyNoInteractions(executionRepository);
    }

    private ProductionExecution execution(WorkOrderOperation operation, String rework) {
        return ProductionExecution.builder().productionExecutionId(UUID.randomUUID()).code("PE-1").workOrder(workOrder)
                .operation(operation).goodQuantity(BigDecimal.ONE).scrapQuantity(BigDecimal.ZERO)
                .reworkQuantity(new BigDecimal(rework)).build();
    }

    private static OpenReceiptQuantityProjection open(UUID workOrderId, String quantity) {
        return new OpenReceiptQuantityProjection() {
            public UUID getWorkOrderId() { return workOrderId; }
            public BigDecimal getOpenQuantity() { return new BigDecimal(quantity); }
        };
    }

    private static WorkOrderReworkTotals reworkTotals(UUID workOrderId, String rework, String retryProcessed) {
        return new WorkOrderReworkTotals() {
            public UUID getWorkOrderId() { return workOrderId; }
            public BigDecimal getRework() { return new BigDecimal(rework); }
            public BigDecimal getRetryProcessed() { return new BigDecimal(retryProcessed); }
        };
    }
}
