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
import com.erp.manufacturing.module.workorder.domain.MaterialIssue;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueLine;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.ProductionExecutionConsumption;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.execution.ExecutionConsumptionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionPostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.KeyedQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.OperationExecutionTotals;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionConsumptionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationInputRepository;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
    @Mock WorkOrderOperationInputRepository inputRepository;
    @Mock ProductionExecutionConsumptionRepository consumptionRepository;
    @Mock MaterialIssueLineRepository issueLineRepository;

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
                new IdempotencySupport(new ObjectMapper().findAndRegisterModules()),
                new TraceIdProvider(),
                auditorAware,
                userLookupService,
                costAccumulatorService,
                new OperationLedger(operationRepository, inputRepository, executionRepository),
                consumptionRepository,
                issueLineRepository);
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
        // G8c (B212): touched before the first write and flushed once at the end — one version step.
        verify(workOrderRepository, times(1)).saveAndFlush(workOrder);
        verify(workOrderRepository, never()).save(any());
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
                foreignOperationId, BigDecimal.ONE, null, null, null, null, null, 0L, null, null);

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

        workOrder.setOperationTracking(false);
        var response = service.report(workOrder.getWorkOrderId(), new ProductionExecutionPostRequest(
                operation.getWorkOrderOperationId(), new BigDecimal("2"), null, null, null, null, null, 0L, null, null),
                "KEY-OP");

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

    // ── G8c: version, operations, pools, retry, consumption, time ──────────────────────────────

    @Test
    void report_withoutExpectedVersion_is400OnThatFieldBeforeAnyWrite() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-NOVER")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        ProductionExecutionPostRequest request = new ProductionExecutionPostRequest(
                null, BigDecimal.ONE, null, null, null, null, null);

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(), request, "KEY-NOVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((com.erp.manufacturing.common.exception.MultiErrorException) ex).getFieldErrors())
                            .containsKey("expectedWorkOrderVersion");
                });
        verify(executionRepository, never()).save(any());
    }

    @Test
    void report_staleVersion_isConcurrentModificationBeforeAnyWrite() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        workOrder.setVersion(3L);
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-STALE")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(), request(BigDecimal.ONE, null, null), "KEY-STALE"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        verify(executionRepository, never()).save(any());
    }

    @Test
    void report_onWorkOrderWithOperations_requiresTheOperation() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderOperation first = operation(workOrder, 10);
        stubLedger(workOrder, List.of(first), Map.of(first.getWorkOrderOperationId(), new BigDecimal("10")), Map.of(), List.of());
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-NOOP")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(), request(BigDecimal.ONE, null, null), "KEY-NOOP"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((com.erp.manufacturing.common.exception.MultiErrorException) ex).getFieldErrors())
                            .containsKey("workOrderOperationId");
                });
        verify(executionRepository, never()).save(any());
    }

    /** B218: fresh work uses input − fresh processed; 8 of 10 used, so 3 more is over the cap. */
    @Test
    void report_freshWorkOverTheConfirmedInputLeft_isPlannedQuantityExceededOnGood() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderOperation first = operation(workOrder, 10);
        stubLedger(workOrder, List.of(first), Map.of(first.getWorkOrderOperationId(), new BigDecimal("10")), Map.of(),
                List.of(totals(first, "6", "1", "1", "8", "0", 1)));
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-FRESH")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(),
                onOperation(first, "2", "1", "0", null, null), "KEY-FRESH"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
                    assertThat(((com.erp.manufacturing.common.exception.MultiErrorException) ex).getFieldErrors())
                            .containsKey("goodQuantity");
                });
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(wipTransactionService);
    }

    @Test
    void report_beforeThePreviousOperationIsFinished_isAnOperationSequenceConflict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderOperation first = operation(workOrder, 10);
        WorkOrderOperation second = operation(workOrder, 20);
        stubLedger(workOrder, List.of(first, second), Map.of(), Map.of(), List.of());
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-SEQ")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(),
                onOperation(second, "1", "0", "0", null, null), "KEY-SEQ"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_SEQUENCE_CONFLICT));
        verify(executionRepository, never()).save(any());
    }

    /** B219: good of an earlier operation is work in progress, not the work order's final good, and earns no labour. */
    @Test
    void report_goodOnAnEarlierOperation_isNotFinalGoodAndAbsorbsNoLabour() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderOperation first = operation(workOrder, 10);
        WorkOrderOperation second = operation(workOrder, 20);
        stubLedger(workOrder, List.of(first, second), Map.of(first.getWorkOrderOperationId(), new BigDecimal("10")),
                Map.of(), List.of());
        stubNewReport(workOrder, "KEY-WIP");

        var response = service.report(workOrder.getWorkOrderId(), onOperation(first, "8", "1", "1", null, null), "KEY-WIP");

        assertThat(workOrder.getActualGoodQuantity()).isEqualByComparingTo("0");
        assertThat(workOrder.getActualScrapQuantity()).isEqualByComparingTo("1");
        assertThat(workOrder.getActualReworkQuantity()).isEqualByComparingTo("1");
        assertThat(response.processedQuantity()).isEqualByComparingTo("10");
        assertThat(response.pendingReworkQuantity()).isEqualByComparingTo("1");
        verifyNoInteractions(costAccumulatorService);
    }

    @Test
    void report_goodOnTheLastOperation_isFinalGood() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderOperation first = operation(workOrder, 10);
        first.finish(Instant.now(), null);
        WorkOrderOperation second = operation(workOrder, 20);
        stubLedger(workOrder, List.of(first, second), Map.of(second.getWorkOrderOperationId(), new BigDecimal("9")),
                Map.of(first.getWorkOrderOperationId(), new BigDecimal("9")), List.of());
        stubNewReport(workOrder, "KEY-FINAL");

        service.report(workOrder.getWorkOrderId(), onOperation(second, "9", "0", "0", null, null), "KEY-FINAL");

        assertThat(workOrder.getActualGoodQuantity()).isEqualByComparingTo("9");
        verify(costAccumulatorService).accumulateLaborOverheadCost(workOrder, new BigDecimal("9"));
    }

    /** B218: a retry draws on its source's pending rework, not on the fresh input — which here is used up. */
    @Test
    void report_retryWithinTheSourcesPendingRework_isAcceptedEvenWithNoFreshInputLeft() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderOperation first = operation(workOrder, 10);
        stubLedger(workOrder, List.of(first), Map.of(first.getWorkOrderOperationId(), new BigDecimal("10")), Map.of(),
                List.of(totals(first, "8", "0", "2", "10", "0", 1)));
        ProductionExecution source = sourceExecution(workOrder, first, "2");
        when(executionRepository.findById(source.getProductionExecutionId())).thenReturn(Optional.of(source));
        when(executionRepository.sumRetryProcessedBySource(List.of(source.getProductionExecutionId())))
                .thenReturn(List.of(keyed(source.getProductionExecutionId(), "1")));
        stubNewReport(workOrder, "KEY-RETRY");

        var response = service.report(workOrder.getWorkOrderId(),
                onOperation(first, "1", "0", "0", source.getProductionExecutionId(), null), "KEY-RETRY");

        assertThat(response.sourceExecutionId()).isEqualTo(source.getProductionExecutionId());
        ArgumentCaptor<ProductionExecution> saved = ArgumentCaptor.forClass(ProductionExecution.class);
        verify(executionRepository).save(saved.capture());
        assertThat(saved.getValue().getSourceExecutionId()).isEqualTo(source.getProductionExecutionId());
    }

    @Test
    void report_retryOverTheSourcesPendingRework_isPlannedQuantityExceeded() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderOperation first = operation(workOrder, 10);
        stubLedger(workOrder, List.of(first), Map.of(first.getWorkOrderOperationId(), new BigDecimal("10")), Map.of(),
                List.of(totals(first, "8", "0", "2", "10", "0", 1)));
        ProductionExecution source = sourceExecution(workOrder, first, "2");
        when(executionRepository.findById(source.getProductionExecutionId())).thenReturn(Optional.of(source));
        when(executionRepository.sumRetryProcessedBySource(List.of(source.getProductionExecutionId())))
                .thenReturn(List.of(keyed(source.getProductionExecutionId(), "1")));
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-RETRY-OVER")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(),
                onOperation(first, "1", "1", "0", source.getProductionExecutionId(), null), "KEY-RETRY-OVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED));
        verify(executionRepository, never()).save(any());
    }

    @Test
    void report_retryOfAnExecutionOfAnotherOperation_isAScopeMismatch() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderOperation first = operation(workOrder, 10);
        first.finish(Instant.now(), null);
        WorkOrderOperation second = operation(workOrder, 20);
        stubLedger(workOrder, List.of(first, second), Map.of(), Map.of(), List.of());
        ProductionExecution source = sourceExecution(workOrder, first, "2");
        when(executionRepository.findById(source.getProductionExecutionId())).thenReturn(Optional.of(source));
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-RETRY-OP")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(),
                onOperation(second, "1", "0", "0", source.getProductionExecutionId(), null), "KEY-RETRY-OP"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH);
                    assertThat(((com.erp.manufacturing.common.exception.MultiErrorException) ex).getFieldErrors())
                            .containsKey("sourceExecutionId");
                });
    }

    /** B220: two entries for the same line are added up first — 3 + 3 on a line with 5 left is over the cap. */
    @Test
    void report_duplicateConsumptionOfOneLine_isAddedUpBeforeTheCap() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        MaterialIssueLine line = postedIssueLine(workOrder, MaterialIssueStatus.POSTED, "8");
        when(issueLineRepository.findWithIssueByIssueLineIdIn(anyCollection())).thenReturn(List.of(line));
        when(consumptionRepository.sumByIssueLineIds(anyCollection())).thenReturn(List.of(keyed(line.getIssueLineId(), "3")));
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-CONS")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        List<ExecutionConsumptionRequest> consumption = List.of(
                new ExecutionConsumptionRequest(line.getIssueLineId(), new BigDecimal("3")),
                new ExecutionConsumptionRequest(line.getIssueLineId(), new BigDecimal("3")));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(),
                new ProductionExecutionPostRequest(null, BigDecimal.ONE, null, null, null, null, null, 0L, null, consumption),
                "KEY-CONS"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
                    assertThat(((com.erp.manufacturing.common.exception.MultiErrorException) ex).getFieldErrors())
                            .containsKey("consumption[0].quantity");
                });
        verify(executionRepository, never()).save(any());
        verify(consumptionRepository, never()).saveAll(any());
    }

    @Test
    void report_consumptionWithinTheLineLeft_isRecordedOncePerLineAndAddedUp() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        MaterialIssueLine line = postedIssueLine(workOrder, MaterialIssueStatus.POSTED, "8");
        when(issueLineRepository.findWithIssueByIssueLineIdIn(anyCollection())).thenReturn(List.of(line));
        when(consumptionRepository.sumByIssueLineIds(anyCollection())).thenReturn(List.of());
        stubNewReport(workOrder, "KEY-CONS-OK");

        service.report(workOrder.getWorkOrderId(), new ProductionExecutionPostRequest(null, BigDecimal.ONE, null, null,
                null, null, null, 0L, null, List.of(
                        new ExecutionConsumptionRequest(line.getIssueLineId(), new BigDecimal("3")),
                        new ExecutionConsumptionRequest(line.getIssueLineId(), new BigDecimal("5")))), "KEY-CONS-OK");

        ArgumentCaptor<List<ProductionExecutionConsumption>> rows = ArgumentCaptor.forClass(List.class);
        verify(consumptionRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).hasSize(1);
        assertThat(rows.getValue().get(0).getIssueLineId()).isEqualTo(line.getIssueLineId());
        assertThat(rows.getValue().get(0).getQuantity()).isEqualByComparingTo("8");
        assertThat(rows.getValue().get(0).getProductionExecution()).isNotNull();
    }

    @Test
    void report_consumptionOfAnotherWorkOrdersLine_isAScopeMismatch() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        MaterialIssueLine line = postedIssueLine(workOrder(WorkOrderStatus.RELEASED, BigDecimal.TEN),
                MaterialIssueStatus.POSTED, "8");
        when(issueLineRepository.findWithIssueByIssueLineIdIn(anyCollection())).thenReturn(List.of(line));
        when(consumptionRepository.sumByIssueLineIds(anyCollection())).thenReturn(List.of());
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-CONS-WO")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(), new ProductionExecutionPostRequest(null,
                BigDecimal.ONE, null, null, null, null, null, 0L, null,
                List.of(new ExecutionConsumptionRequest(line.getIssueLineId(), BigDecimal.ONE))), "KEY-CONS-WO"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));
    }

    @Test
    void report_consumptionOfAPendingIssue_isAStateConflict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        MaterialIssueLine line = postedIssueLine(workOrder, MaterialIssueStatus.PENDING_APPROVAL, "8");
        when(issueLineRepository.findWithIssueByIssueLineIdIn(anyCollection())).thenReturn(List.of(line));
        when(consumptionRepository.sumByIssueLineIds(anyCollection())).thenReturn(List.of());
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-CONS-PEND")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(), new ProductionExecutionPostRequest(null,
                BigDecimal.ONE, null, null, null, null, null, 0L, null,
                List.of(new ExecutionConsumptionRequest(line.getIssueLineId(), BigDecimal.ONE))), "KEY-CONS-PEND"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    /** FE Q22: a work order reported before V81 has no input evidence — retries and consumption are refused, not guessed. */
    @Test
    void report_consumptionOnAWorkOrderWithoutInputEvidence_isProductionInputUnknown() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.setOperationTracking(false);
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-LEGACY")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(), new ProductionExecutionPostRequest(null,
                BigDecimal.ONE, null, null, null, null, null, 0L, null,
                List.of(new ExecutionConsumptionRequest(UUID.randomUUID(), BigDecimal.ONE))), "KEY-LEGACY"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PRODUCTION_INPUT_UNKNOWN));
        verifyNoInteractions(issueLineRepository, consumptionRepository);
    }

    /** FE Q31: up to a minute of tablet clock skew is accepted; beyond it the end time is a field error. */
    @Test
    void report_endTimeMoreThanAMinuteAhead_is400OnActualEndedAt() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        when(executionRepository.findWithDetailsByIdempotencyKey("KEY-FUTURE")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        Instant now = Instant.now();

        assertThatThrownBy(() -> service.report(workOrder.getWorkOrderId(), new ProductionExecutionPostRequest(null,
                BigDecimal.ONE, null, null, now.minusSeconds(600), now.plusSeconds(120), null, 0L, null, null), "KEY-FUTURE"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((com.erp.manufacturing.common.exception.MultiErrorException) ex)
                        .getFieldErrors()).containsKey("actualEndedAt"));
        verify(executionRepository, never()).save(any());
    }

    @Test
    void report_endTimeWithinTheMinuteOfSkew_isAccepted() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        stubNewReport(workOrder, "KEY-SKEW");
        Instant now = Instant.now();

        var response = service.report(workOrder.getWorkOrderId(), new ProductionExecutionPostRequest(null,
                BigDecimal.ONE, null, null, now.minusSeconds(600), now.plusSeconds(30), null, 0L, null, null), "KEY-SKEW");

        assertThat(response.durationMinutes()).isEqualTo(10L);
    }

    private void stubLedger(WorkOrder workOrder, List<WorkOrderOperation> operations, Map<UUID, BigDecimal> inputs,
                            Map<UUID, BigDecimal> transferred, List<OperationExecutionTotals> totals) {
        UUID id = workOrder.getWorkOrderId();
        when(operationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(id)).thenReturn(operations);
        when(inputRepository.sumInputByOperation(id)).thenReturn(inputs.entrySet().stream()
                .map(entry -> keyed(entry.getKey(), entry.getValue().toPlainString())).toList());
        when(inputRepository.sumTransferredBySourceOperation(id)).thenReturn(transferred.entrySet().stream()
                .map(entry -> keyed(entry.getKey(), entry.getValue().toPlainString())).toList());
        when(executionRepository.sumByOperation(id)).thenReturn(totals);
    }

    private ProductionExecutionPostRequest onOperation(WorkOrderOperation operation, String good, String scrap,
                                                       String rework, UUID sourceExecutionId,
                                                       List<ExecutionConsumptionRequest> consumption) {
        return new ProductionExecutionPostRequest(operation.getWorkOrderOperationId(), new BigDecimal(good),
                new BigDecimal(scrap), new BigDecimal(rework), null, null, null, 0L, sourceExecutionId, consumption);
    }

    private static WorkOrderOperation operation(WorkOrder workOrder, int sequence) {
        return WorkOrderOperation.builder()
                .workOrderOperationId(UUID.randomUUID())
                .workOrder(workOrder)
                .sequence(sequence)
                .name("OP-" + sequence)
                .workCenterCode("WC-" + sequence)
                .setupMinutes(BigDecimal.ZERO)
                .runMinutesPerUnit(BigDecimal.ONE)
                .build();
    }

    private static ProductionExecution sourceExecution(WorkOrder workOrder, WorkOrderOperation operation, String rework) {
        return ProductionExecution.builder()
                .productionExecutionId(UUID.randomUUID())
                .workOrder(workOrder)
                .operation(operation)
                .goodQuantity(new BigDecimal("8"))
                .scrapQuantity(BigDecimal.ZERO)
                .reworkQuantity(new BigDecimal(rework))
                .build();
    }

    private static MaterialIssueLine postedIssueLine(WorkOrder workOrder, MaterialIssueStatus status, String quantity) {
        MaterialIssue issue = MaterialIssue.builder()
                .issueId(UUID.randomUUID())
                .workOrder(workOrder)
                .status(status)
                .build();
        return MaterialIssueLine.builder()
                .issueLineId(UUID.randomUUID())
                .issue(issue)
                .quantity(new BigDecimal(quantity))
                .build();
    }

    static KeyedQuantityProjection keyed(UUID id, String quantity) {
        return new KeyedQuantityProjection() {
            public UUID getRefId() { return id; }
            public BigDecimal getQuantity() { return new BigDecimal(quantity); }
        };
    }

    static OperationExecutionTotals totals(WorkOrderOperation operation, String good, String scrap, String rework,
                                           String freshProcessed, String retryProcessed, long count) {
        return new OperationExecutionTotals() {
            public UUID getOperationId() { return operation.getWorkOrderOperationId(); }
            public BigDecimal getGood() { return new BigDecimal(good); }
            public BigDecimal getScrap() { return new BigDecimal(scrap); }
            public BigDecimal getRework() { return new BigDecimal(rework); }
            public BigDecimal getFreshProcessed() { return new BigDecimal(freshProcessed); }
            public BigDecimal getRetryProcessed() { return new BigDecimal(retryProcessed); }
            public Long getExecutionCount() { return count; }
        };
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
        return new ProductionExecutionPostRequest(null, good, scrap, rework, null, null, "Shift A", 0L, null, null);
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
        WorkOrder workOrder = WorkOrder.builder()
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
        workOrder.setVersion(0L);
        return workOrder;
    }
}
