package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.costing.service.ItemStandardCostLookupService;
import com.erp.manufacturing.module.costing.service.StandardCostBreakdown;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.repository.*;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderVarianceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderVarianceService tests")
class WorkOrderVarianceServiceTest {

    @Mock MaterialIssueLineRepository materialIssueLineRepository;
    @Mock ProductionReceiptLineRepository productionReceiptLineRepository;
    @Mock WipTransactionRepository wipTransactionRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock ProductionExecutionRepository productionExecutionRepository;
    @Mock WorkOrderOperationRepository workOrderOperationRepository;
    @Mock WorkOrderCostAccumulatorRepository workOrderCostAccumulatorRepository;
    @Mock ItemStandardCostLookupService itemStandardCostLookupService;

    WorkOrderVarianceService service;

    private static final StandardCostBreakdown ZERO_BREAKDOWN =
            new StandardCostBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    @BeforeEach
    void setUp() {
        service = new WorkOrderVarianceService(
                materialIssueLineRepository,
                productionReceiptLineRepository,
                wipTransactionRepository,
                workOrderRepository,
                productionExecutionRepository,
                workOrderOperationRepository,
                workOrderCostAccumulatorRepository,
                itemStandardCostLookupService);
        // Every test calls getVariance(), which always computes the cost variance block; only
        // tests with component lines also hit findStandardUnitCost — lenient so those without
        // lines don't trip strict-stubbing on an unused default.
        lenient().when(itemStandardCostLookupService.findStandardCostBreakdown(any(), any()))
                .thenReturn(ZERO_BREAKDOWN);
        lenient().when(itemStandardCostLookupService.findStandardUnitCost(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(workOrderCostAccumulatorRepository.findByWorkOrderWorkOrderId(any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void getVariance_calculatesMaterialOutputAndWipSummary() {
        UUID workOrderId = UUID.randomUUID();
        UUID componentLineId = UUID.randomUUID();
        Item product = item(UUID.randomUUID(), "FG-100", ItemType.FINISHED_GOOD);
        Item component = item(UUID.randomUUID(), "RM-001", ItemType.RAW_MATERIAL);
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(workOrderId)
                .workOrderNo("WO-001")
                .company(product.getCompany())
                .productItem(product)
                .plannedQuantity(new BigDecimal("10"))
                .status(WorkOrderStatus.COMPLETED)
                .componentLines(new ArrayList<>())
                .build();
        workOrder.getComponentLines().add(WorkOrderComponentLine.builder()
                .componentLineId(componentLineId)
                .componentItem(component)
                .bomLine(BomLine.builder().lineId(UUID.randomUUID()).build())
                .lineNo(10)
                .requiredQuantity(new BigDecimal("12"))
                .issuedQuantity(new BigDecimal("13"))
                .build());

        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(workOrder));
        when(materialIssueLineRepository.sumIssuedByWorkOrder(workOrderId))
                .thenReturn(List.of(new TestQuantity(componentLineId, new BigDecimal("13"))));
        when(productionReceiptLineRepository.sumReceivedQuantityByWorkOrder(workOrderId))
                .thenReturn(new BigDecimal("9"));
        when(wipTransactionRepository.sumQuantityByWorkOrderAndType(workOrderId, WipTransactionType.SCRAP_REPORTED))
                .thenReturn(new BigDecimal("1"));
        when(wipTransactionRepository.sumQuantityByWorkOrderAndType(workOrderId, WipTransactionType.REWORK_REPORTED))
                .thenReturn(new BigDecimal("2"));
        when(itemStandardCostLookupService.findStandardUnitCost(
                product.getCompany().getCompanyId(), component.getItemId()))
                .thenReturn(new BigDecimal("4.50"));

        var response = service.getVariance(workOrderId);

        assertThat(response.materialLines().get(0).varianceQuantity()).isEqualByComparingTo("1");
        assertThat(response.materialLines().get(0).status()).isEqualTo(VarianceStatus.OVER_ISSUED.name());
        // varianceQuantity (1) * standardUnitCost (4.50) — P3 Material Usage Variance
        assertThat(response.materialLines().get(0).usageVarianceCost()).isEqualByComparingTo("4.50");
        assertThat(response.outputVariance().varianceQuantity()).isEqualByComparingTo("-1");
        assertThat(response.wipSummary().scrapQuantity()).isEqualByComparingTo("1");
        assertThat(response.wipSummary().reworkQuantity()).isEqualByComparingTo("2");
    }

    // ── cost variance (P3) ──────────────────────────────────────────────────

    @Test
    void getVariance_costVariance_standardIsBreakdownTimesPlannedQuantity_actualIsZeroWithoutAnAccumulator() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = baseWorkOrder(workOrderId, new BigDecimal("10"));
        stubEmptyVarianceInputs(workOrderId);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(workOrderId))
                .thenReturn(List.of());
        when(productionExecutionRepository.findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrderId))
                .thenReturn(List.of());
        when(itemStandardCostLookupService.findStandardCostBreakdown(
                workOrder.getCompany().getCompanyId(), workOrder.getProductItem().getItemId()))
                .thenReturn(new StandardCostBreakdown(new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("1")));
        // No accumulator row — a work order that has issued nothing and reported nothing yet.
        when(workOrderCostAccumulatorRepository.findByWorkOrderWorkOrderId(workOrderId))
                .thenReturn(Optional.empty());

        var response = service.getVariance(workOrderId);

        var costVariance = response.costVariance();
        assertThat(costVariance.standardMaterialCost()).isEqualByComparingTo("20");  // 2 * 10
        assertThat(costVariance.standardLaborCost()).isEqualByComparingTo("30");     // 3 * 10
        assertThat(costVariance.standardOverheadCost()).isEqualByComparingTo("10");  // 1 * 10
        assertThat(costVariance.standardTotalCost()).isEqualByComparingTo("60");
        assertThat(costVariance.actualMaterialCost()).isEqualByComparingTo("0");
        assertThat(costVariance.actualLaborCost()).isEqualByComparingTo("0");
        assertThat(costVariance.actualOverheadCost()).isEqualByComparingTo("0");
        assertThat(costVariance.actualTotalCost()).isEqualByComparingTo("0");
        assertThat(costVariance.totalCostVariance()).isEqualByComparingTo("-60");
    }

    @Test
    void getVariance_costVariance_actualReadsTheAccumulatorRowWhenItExists() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = baseWorkOrder(workOrderId, new BigDecimal("10"));
        stubEmptyVarianceInputs(workOrderId);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(workOrderId))
                .thenReturn(List.of());
        when(productionExecutionRepository.findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrderId))
                .thenReturn(List.of());
        WorkOrderCostAccumulator accumulator = WorkOrderCostAccumulator.builder()
                .workOrderCostAccumulatorId(UUID.randomUUID())
                .workOrder(workOrder)
                .materialCostAccumulated(new BigDecimal("25"))
                .laborCostAccumulated(new BigDecimal("35"))
                .overheadCostAccumulated(new BigDecimal("15"))
                .build();
        when(workOrderCostAccumulatorRepository.findByWorkOrderWorkOrderId(workOrderId))
                .thenReturn(Optional.of(accumulator));

        var response = service.getVariance(workOrderId);

        var costVariance = response.costVariance();
        assertThat(costVariance.actualMaterialCost()).isEqualByComparingTo("25");
        assertThat(costVariance.actualLaborCost()).isEqualByComparingTo("35");
        assertThat(costVariance.actualOverheadCost()).isEqualByComparingTo("15");
        assertThat(costVariance.actualTotalCost()).isEqualByComparingTo("75");
        // standard is the ZERO_BREAKDOWN default from setUp() * plannedQuantity = 0
        assertThat(costVariance.standardTotalCost()).isEqualByComparingTo("0");
        assertThat(costVariance.totalCostVariance()).isEqualByComparingTo("75");
    }

    // ── time variance (C2-4) ───────────────────────────────────────────────

    @Test
    void getVariance_timeVariance_sumsOperationsAndExecutionsExcludingInProgress() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = baseWorkOrder(workOrderId, new BigDecimal("10"));
        stubEmptyVarianceInputs(workOrderId);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(workOrderId))
                .thenReturn(List.of(
                        operation(new BigDecimal("15"), new BigDecimal("2")),   // 15 + 2*10 = 35
                        operation(new BigDecimal("5"), new BigDecimal("1"))));  // 5 + 1*10 = 15

        Instant start = Instant.parse("2026-08-01T08:00:00Z");
        when(productionExecutionRepository.findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrderId))
                .thenReturn(List.of(
                        execution(start, start.plusSeconds(30 * 60)),        // 30 min
                        execution(start, start.plusSeconds(20 * 60)),        // 20 min
                        execution(start, null)));                           // in-progress, excluded

        var response = service.getVariance(workOrderId);

        assertThat(response.timeVariance().plannedMinutes()).isEqualByComparingTo("50");
        assertThat(response.timeVariance().actualMinutes()).isEqualByComparingTo("50");
        assertThat(response.timeVariance().varianceMinutes()).isEqualByComparingTo("0");
    }

    @Test
    void getVariance_noOperations_plannedMinutesIsZero() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = baseWorkOrder(workOrderId, new BigDecimal("10"));
        stubEmptyVarianceInputs(workOrderId);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(workOrder));
        when(productionExecutionRepository.findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrderId))
                .thenReturn(List.of());

        var response = service.getVariance(workOrderId);

        assertThat(response.timeVariance().plannedMinutes()).isEqualByComparingTo("0");
    }

    @Test
    void getVariance_noExecutions_actualMinutesIsZero() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = baseWorkOrder(workOrderId, new BigDecimal("10"));
        stubEmptyVarianceInputs(workOrderId);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(workOrderId))
                .thenReturn(List.of(operation(new BigDecimal("15"), new BigDecimal("2"))));
        when(productionExecutionRepository.findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrderId))
                .thenReturn(List.of());

        var response = service.getVariance(workOrderId);

        assertThat(response.timeVariance().actualMinutes()).isEqualByComparingTo("0");
        assertThat(response.timeVariance().plannedMinutes()).isEqualByComparingTo("35");
    }

    private WorkOrder baseWorkOrder(UUID workOrderId, BigDecimal plannedQuantity) {
        Item product = item(UUID.randomUUID(), "FG-100", ItemType.FINISHED_GOOD);
        return WorkOrder.builder()
                .workOrderId(workOrderId)
                .workOrderNo("WO-001")
                .company(product.getCompany())
                .productItem(product)
                .plannedQuantity(plannedQuantity)
                .status(WorkOrderStatus.IN_PROGRESS)
                .componentLines(new ArrayList<>())
                .build();
    }

    private void stubEmptyVarianceInputs(UUID workOrderId) {
        when(materialIssueLineRepository.sumIssuedByWorkOrder(workOrderId)).thenReturn(List.of());
        when(productionReceiptLineRepository.sumReceivedQuantityByWorkOrder(workOrderId)).thenReturn(BigDecimal.ZERO);
        when(wipTransactionRepository.sumQuantityByWorkOrderAndType(workOrderId, WipTransactionType.SCRAP_REPORTED))
                .thenReturn(BigDecimal.ZERO);
        when(wipTransactionRepository.sumQuantityByWorkOrderAndType(workOrderId, WipTransactionType.REWORK_REPORTED))
                .thenReturn(BigDecimal.ZERO);
    }

    private WorkOrderOperation operation(BigDecimal setupMinutes, BigDecimal runMinutesPerUnit) {
        return WorkOrderOperation.builder()
                .workOrderOperationId(UUID.randomUUID())
                .sequence(1)
                .name("Assembly")
                .workCenterCode("WC-1")
                .setupMinutes(setupMinutes)
                .runMinutesPerUnit(runMinutesPerUnit)
                .build();
    }

    private ProductionExecution execution(Instant startedAt, Instant endedAt) {
        return ProductionExecution.builder()
                .productionExecutionId(UUID.randomUUID())
                .actualStartedAt(startedAt)
                .actualEndedAt(endedAt)
                .build();
    }

    private record TestQuantity(UUID componentLineId, BigDecimal quantity) implements ComponentQuantityProjection {
        @Override public UUID getComponentLineId() { return componentLineId; }
        @Override public BigDecimal getQuantity() { return quantity; }
    }

    private Item item(UUID itemId, String code, ItemType type) {
        return Item.builder()
                .itemId(itemId)
                .company(Company.builder()
                        .companyId(UUID.randomUUID())
                        .code("ACME")
                        .name("ACME")
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .code(code)
                .name(code)
                .type(type)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }
}
