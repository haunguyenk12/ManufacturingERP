package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionPostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guards the F5 semantic inversion (CLAUDE.md §0.5): the shop floor, not the production receipt,
 * is what advances and completes a work order.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductionExecutionService tests")
class ProductionExecutionServiceTest {

    @Mock ProductionExecutionRepository executionRepository;
    @Mock WorkOrderOperationRepository operationRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock WipTransactionService wipTransactionService;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;
    @Mock SecurityAuditorAware auditorAware;
    @Mock UserLookupService userLookupService;
    @Mock WorkOrderCostAccumulatorService costAccumulatorService;

    ProductionExecutionService service;

    @BeforeEach
    void setUp() {
        WorkOrderExecutionSupport support = new WorkOrderExecutionSupport(
                workOrderRepository, organizationLookupService, inventoryAvailabilityService);
        service = new ProductionExecutionService(
                executionRepository,
                operationRepository,
                workOrderRepository,
                wipTransactionService,
                support,
                new ManufacturingExecutionMapper(),
                new IdempotencySupport(new ObjectMapper()),
                new TraceIdProvider(),
                auditorAware,
                userLookupService,
                costAccumulatorService);
    }

    /**
     * The candidates screen (spec §5.1). What the <em>filtering</em> does is JPQL and therefore lives
     * in {@code WorkOrderRepositoryIT} (rule R7); what this asserts is the part that is Java: only the
     * two reportable statuses are asked for, and the row is mapped with the remaining quantity the
     * operator needs — {@code plannedQuantity - actualGoodQuantity}, not the plan.
     */
    @Test
    void listCandidates_asksOnlyForReportableStatuses_andReportsWhatIsLeftToProduce() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.setActualGoodQuantity(new BigDecimal("6"));
        UUID plantId = workOrder.getPlant().getPlantId();
        when(workOrderRepository.findExecutionCandidates(eq(plantId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(workOrder)));

        var page = service.listCandidates(plantId, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).workOrderId()).isEqualTo(workOrder.getWorkOrderId());
        assertThat(page.content().get(0).remainingQuantity()).isEqualByComparingTo("4");
        assertThat(page.content().get(0).itemSku()).isEqualTo("FG-100");
        // Spec §5.2 "Context: outputItemSku, outputItemName, uom, plannedQuantity" (added in F9).
        assertThat(page.content().get(0).uom()).isEqualTo("EA");

        ArgumentCaptor<Collection<WorkOrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(workOrderRepository).findExecutionCandidates(eq(plantId), statuses.capture(), any());
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS);
    }

    /**
     * Spec §5.2 asks the history to show {@code operatorUsername}, and rule C14 says a page must
     * resolve them in <em>one</em> query. Both halves matter, and the id one is the subtle half: the
     * name to render is the operator who posted the report, not whoever created the work order.
     * Getting that wrong still returns HTTP 200 with a plausible-looking name.
     */
    @Test
    void list_resolvesTheOperatorUsernameInOneBatchQuery() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.setActualGoodQuantity(new BigDecimal("6"));
        workOrder.setCreatedBy(UUID.randomUUID());
        UUID operatorId = UUID.randomUUID();

        ProductionExecution first = execution(workOrder, operatorId, new BigDecimal("4"));
        ProductionExecution second = execution(workOrder, operatorId, new BigDecimal("2"));

        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(executionRepository.findByWorkOrderWorkOrderId(eq(workOrder.getWorkOrderId()), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(userLookupService.findUsernames(any())).thenReturn(Map.of(operatorId, "operator1"));

        var page = service.list(workOrder.getWorkOrderId(), PageRequest.of(0, 20));

        assertThat(page.content()).extracting(ProductionExecutionResponse::operatorUsername)
                .containsExactly("operator1", "operator1");
        // C15: one batch for the whole page, never one lookup per row.
        verify(userLookupService, times(1)).findUsernames(any());

        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(userLookupService).findUsernames(ids.capture());
        assertThat(ids.getValue()).containsOnly(operatorId);
        assertThat(ids.getValue()).doesNotContain(workOrder.getCreatedBy());
    }

    /** Spec §5.2 "Summary": both numbers come off the work order, at the scale the badge renders. */
    @Test
    void list_reportsTheWorkOrderSummaryAlongsideEachPosting() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.setActualGoodQuantity(new BigDecimal("6"));
        ProductionExecution execution = execution(workOrder, UUID.randomUUID(), new BigDecimal("6"));

        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(executionRepository.findByWorkOrderWorkOrderId(eq(workOrder.getWorkOrderId()), any()))
                .thenReturn(new PageImpl<>(List.of(execution)));
        when(userLookupService.findUsernames(any())).thenReturn(Map.of());

        ProductionExecutionResponse response = service.list(
                workOrder.getWorkOrderId(), PageRequest.of(0, 20)).content().get(0);

        assertThat(response.workOrderCode()).isEqualTo(workOrder.getWorkOrderNo());
        assertThat(response.uom()).isEqualTo("EA");
        assertThat(response.workOrderRemainingGoodQuantity()).isEqualByComparingTo("4");
        assertThat(response.workOrderCompletionPercent()).isEqualByComparingTo("60.00");
    }

    @Test
    void report_good_increasesActualGoodAndAvailableToReceipt() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        stubNewReport(workOrder, "KEY-EXEC");

        var response = service.report(workOrder.getWorkOrderId(),
                request(new BigDecimal("4"), null, null), "KEY-EXEC");

        assertThat(workOrder.getActualGoodQuantity()).isEqualByComparingTo("4");
        assertThat(workOrder.availableToReceipt()).isEqualByComparingTo("4");
        assertThat(response.workOrderAvailableToReceipt()).isEqualByComparingTo("4");
        // Reporting output starts the work order, exactly as issuing material does.
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(workOrderRepository).save(workOrder);
    }

    @Test
    void report_good_accumulatesLaborAndOverheadCost() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        stubNewReport(workOrder, "KEY-COST");

        service.report(workOrder.getWorkOrderId(), request(new BigDecimal("4"), null, null), "KEY-COST");

        verify(costAccumulatorService).accumulateLaborOverheadCost(workOrder, new BigDecimal("4"));
    }

    @Test
    void report_scrapAndReworkOnly_accumulatesNothing() {
        // Only the GOOD quantity earns labor/overhead cost — scrap/rework are not sellable output.
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        stubNewReport(workOrder, "KEY-NO-GOOD");

        service.report(workOrder.getWorkOrderId(),
                request(null, new BigDecimal("2"), new BigDecimal("3")), "KEY-NO-GOOD");

        verifyNoInteractions(costAccumulatorService);
    }

    @Test
    void report_cumulativeGoodReachesPlanned_completesWorkOrderWithoutAnyReceipt() {
        // The proof that the inversion is done: no receipt exists and the work order still closes.
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.setActualGoodQuantity(new BigDecimal("6"));
        stubNewReport(workOrder, "KEY-DONE");

        service.report(workOrder.getWorkOrderId(), request(new BigDecimal("4"), null, null), "KEY-DONE");

        assertThat(workOrder.getActualGoodQuantity()).isEqualByComparingTo("10");
        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("0");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(workOrder.getCompletedAt()).isNotNull();
    }

    @Test
    void report_scrapAndRework_doNotCountTowardsCompletion() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.setActualGoodQuantity(new BigDecimal("9"));
        stubNewReport(workOrder, "KEY-SCRAP");

        service.report(workOrder.getWorkOrderId(),
                request(null, new BigDecimal("2"), new BigDecimal("3")), "KEY-SCRAP");

        assertThat(workOrder.getActualGoodQuantity()).isEqualByComparingTo("9");
        assertThat(workOrder.getActualScrapQuantity()).isEqualByComparingTo("2");
        assertThat(workOrder.getActualReworkQuantity()).isEqualByComparingTo("3");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(wipTransactionService).recordProductionExecution(
                eq(workOrder), eq(null), eq(WipTransactionType.SCRAP_REPORTED), eq(new BigDecimal("2")), any());
        verify(wipTransactionService).recordProductionExecution(
                eq(workOrder), eq(null), eq(WipTransactionType.REWORK_REPORTED), eq(new BigDecimal("3")), any());
        verify(wipTransactionService, never()).recordProductionExecution(
                any(), any(), eq(WipTransactionType.OUTPUT_COMPLETED), any(), any());
    }

    @Test
    void report_cumulativeGoodOverPlanned_throwsPlannedQuantityExceededBeforeAnyWrite() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.setActualGoodQuantity(new BigDecimal("8"));
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-OVER")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        UUID workOrderId = workOrder.getWorkOrderId();
        ProductionExecutionPostRequest request = request(new BigDecimal("3"), null, null);

        assertThatThrownBy(() -> service.report(workOrderId, request, "KEY-OVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED));

        assertThat(workOrder.getActualGoodQuantity()).isEqualByComparingTo("8");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(wipTransactionService);
    }

    @Test
    void report_onBlockedWorkOrder_throwsStateConflict() {
        // B13: BLOCKED must not produce anything, the same rule that guards issue and receipt.
        WorkOrder workOrder = workOrder(WorkOrderStatus.BLOCKED, new BigDecimal("10"));
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-BLOCKED")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        UUID workOrderId = workOrder.getWorkOrderId();
        ProductionExecutionPostRequest request = request(BigDecimal.ONE, null, null);

        assertThatThrownBy(() -> service.report(workOrderId, request, "KEY-BLOCKED"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verify(executionRepository, never()).save(any());
        verifyNoInteractions(wipTransactionService);
    }

    @Test
    void report_allQuantitiesZero_throwsNegativeQuantity() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-EMPTY")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        UUID workOrderId = workOrder.getWorkOrderId();
        ProductionExecutionPostRequest request = request(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> service.report(workOrderId, request, "KEY-EMPTY"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.NEGATIVE_QUANTITY));

        verify(executionRepository, never()).save(any());
    }

    @Test
    void report_operationOfAnotherWorkOrder_throwsResourceNotFound() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        UUID foreignOperationId = UUID.randomUUID();
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-FOREIGN")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(operationRepository.findByWorkOrderOperationIdAndWorkOrderWorkOrderId(
                foreignOperationId, workOrder.getWorkOrderId())).thenReturn(Optional.empty());

        UUID workOrderId = workOrder.getWorkOrderId();
        ProductionExecutionPostRequest request = new ProductionExecutionPostRequest(
                foreignOperationId, BigDecimal.ONE, null, null, null, null, null);

        assertThatThrownBy(() -> service.report(workOrderId, request, "KEY-FOREIGN"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));

        verify(executionRepository, never()).save(any());
        verifyNoInteractions(wipTransactionService);
    }

    @Test
    void report_againstOperation_linksWipToThatOperation() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderOperation operation = WorkOrderOperation.builder()
                .workOrderOperationId(UUID.randomUUID())
                .workOrder(workOrder)
                .sequence(10)
                .name("Assembly")
                .workCenterCode("WC-01")
                .setupMinutes(BigDecimal.ZERO)
                .runMinutesPerUnit(BigDecimal.ONE)
                .build();
        stubNewReport(workOrder, "KEY-OP");
        when(operationRepository.findByWorkOrderOperationIdAndWorkOrderWorkOrderId(
                operation.getWorkOrderOperationId(), workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(operation));

        var response = service.report(workOrder.getWorkOrderId(), new ProductionExecutionPostRequest(
                operation.getWorkOrderOperationId(), new BigDecimal("2"), null, null, null, null, null), "KEY-OP");

        assertThat(response.workOrderOperationId()).isEqualTo(operation.getWorkOrderOperationId());
        assertThat(response.operationSequence()).isEqualTo(10);
        assertThat(response.workCenterCode()).isEqualTo("WC-01");
        verify(wipTransactionService).recordProductionExecution(
                eq(workOrder), eq(operation), eq(WipTransactionType.OUTPUT_COMPLETED),
                eq(new BigDecimal("2")), any());
    }

    @Test
    void report_duplicateIdempotencyKey_returnsExistingReportWithoutTouchingWorkOrder() {
        // R9: replaying the same key must not double-count production.
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.setActualGoodQuantity(new BigDecimal("4"));
        ProductionExecution existing = ProductionExecution.builder()
                .productionExecutionId(UUID.randomUUID())
                .workOrder(workOrder)
                .goodQuantity(new BigDecimal("4"))
                .scrapQuantity(BigDecimal.ZERO)
                .reworkQuantity(BigDecimal.ZERO)
                .idempotencyKey("KEY-DUP")
                .build();
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-DUP")).thenReturn(Optional.of(existing));

        var response = service.report(workOrder.getWorkOrderId(),
                request(new BigDecimal("4"), null, null), "KEY-DUP");

        assertThat(response.productionExecutionId()).isEqualTo(existing.getProductionExecutionId());
        assertThat(workOrder.getActualGoodQuantity()).isEqualByComparingTo("4");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(wipTransactionService, workOrderRepository);
    }

    private void stubNewReport(WorkOrder workOrder, String key) {
        when(executionRepository.findWithDetailsByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(executionRepository.save(any(ProductionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
    }

    private ProductionExecution execution(WorkOrder workOrder, UUID operatorUserId, BigDecimal good) {
        return ProductionExecution.builder()
                .productionExecutionId(UUID.randomUUID())
                .workOrder(workOrder)
                .goodQuantity(good)
                .scrapQuantity(BigDecimal.ZERO)
                .reworkQuantity(BigDecimal.ZERO)
                .operatorUserId(operatorUserId)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
    }

    private ProductionExecutionPostRequest request(BigDecimal good, BigDecimal scrap, BigDecimal rework) {
        return new ProductionExecutionPostRequest(null, good, scrap, rework, null, null, "Shift A");
    }

    private WorkOrder workOrder(WorkOrderStatus status, BigDecimal plannedQuantity) {
        Company company = Company.builder()
                .companyId(UUID.randomUUID())
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Plant plant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Item product = Item.builder()
                .itemId(UUID.randomUUID())
                .company(company)
                .code("FG-100")
                .name("FG-100")
                .type(ItemType.FINISHED_GOOD)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
        return WorkOrder.builder()
                .workOrderId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .workOrderNo("WO-001")
                .productItem(product)
                .plannedQuantity(plannedQuantity)
                .completedQuantity(BigDecimal.ZERO)
                .actualGoodQuantity(BigDecimal.ZERO)
                .actualScrapQuantity(BigDecimal.ZERO)
                .actualReworkQuantity(BigDecimal.ZERO)
                .status(status)
                .componentLines(new ArrayList<>())
                .operations(new ArrayList<>())
                .build();
    }
}
