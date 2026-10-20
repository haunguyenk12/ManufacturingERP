package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
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
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCommand;
import com.erp.manufacturing.module.workorder.domain.WorkOrderIdempotencyRecord;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperationInput;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest;
import com.erp.manufacturing.module.workorder.dto.operation.OperationInputRequest;
import com.erp.manufacturing.module.workorder.dto.operation.ProductionCompletionRequest;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.repository.OperationExecutionTotals;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderIdempotencyRecordRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationInputRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** G8c: the operation ledger commands — input, finish and complete-production (FE R10, R14, D1, D6). */
@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock WorkOrderOperationRepository operationRepository;
    @Mock WorkOrderOperationInputRepository inputRepository;
    @Mock ProductionExecutionRepository executionRepository;
    @Mock MaterialIssueLineRepository issueLineRepository;
    @Mock MaterialIssueRepository issueRepository;
    @Mock WorkOrderIdempotencyRecordRepository idempotencyRecordRepository;
    @Mock WorkOrderPermissionGuard permissionGuard;
    @Mock SecurityAuditorAware auditorAware;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;

    OperationService service;
    WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        WorkOrderExecutionSupport support = new WorkOrderExecutionSupport(
                workOrderRepository, organizationLookupService, inventoryAvailabilityService);
        service = new OperationService(
                new OperationLedger(operationRepository, inputRepository, executionRepository),
                support, inputRepository, issueLineRepository, issueRepository,
                new WorkOrderIdempotency(idempotencyRecordRepository,
                        new IdempotencySupport(new ObjectMapper().findAndRegisterModules())),
                permissionGuard, auditorAware);
        workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        lenient().when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        lenient().when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── list ────────────────────────────────────────────────────────────────────────────────

    @Test
    void list_derivesStatusAndPoolsFromTheLedgers() {
        WorkOrderOperation first = operation(10);
        first.finish(Instant.now(), null);
        WorkOrderOperation second = operation(20);
        WorkOrderOperation third = operation(30);
        stubLedger(List.of(first, second, third),
                Map.of(first.getWorkOrderOperationId(), "10", second.getWorkOrderOperationId(), "6"),
                Map.of(first.getWorkOrderOperationId(), "6"),
                List.of(totals(first, "8", "1", "1", "10", "0", 2),
                        totals(second, "2", "0", "1", "3", "0", 1)));

        var rows = service.list(workOrder.getWorkOrderId());

        assertThat(rows).extracting(r -> r.status()).containsExactly("COMPLETED", "IN_PROGRESS", "PENDING");
        var o1 = rows.get(0);
        assertThat(o1.freshRemainingQuantity()).isEqualByComparingTo("0");
        assertThat(o1.pendingReworkQuantity()).isEqualByComparingTo("1");
        assertThat(o1.transferredQuantity()).isEqualByComparingTo("6");
        assertThat(o1.transferableQuantity()).isEqualByComparingTo("2");
        var o2 = rows.get(1);
        assertThat(o2.inputQuantity()).isEqualByComparingTo("6");
        assertThat(o2.freshRemainingQuantity()).isEqualByComparingTo("3");
        assertThat(rows.get(2).transferableQuantity()).isNull();
        assertThat(rows.get(2).transferredQuantity()).isNull();
    }

    /** FE Q22: a work order reported before V81 has no input evidence — input and caps are null, never 0. */
    @Test
    void list_onAWorkOrderWithoutInputEvidence_reportsInputAndCapsAsUnknown() {
        workOrder.setOperationTracking(false);
        WorkOrderOperation only = operation(10);
        stubLedger(List.of(only), Map.of(), Map.of(), List.of(totals(only, "4", "0", "0", "4", "0", 1)));

        var row = service.list(workOrder.getWorkOrderId()).get(0);

        assertThat(row.inputKnown()).isFalse();
        assertThat(row.inputQuantity()).isNull();
        assertThat(row.freshRemainingQuantity()).isNull();
        assertThat(row.goodQuantity()).isEqualByComparingTo("4");
    }

    // ── input ───────────────────────────────────────────────────────────────────────────────

    @Test
    void input_firstOperationBeforeAnyIssue_isMaterialIssueRequired() {
        WorkOrderOperation first = operation(10);
        stubLedger(List.of(first), Map.of(), Map.of(), List.of());
        when(issueLineRepository.sumIssuedQuantityByWorkOrder(workOrder.getWorkOrderId())).thenReturn(BigDecimal.ZERO);

        assertCode(() -> service.recordInput(workOrder.getWorkOrderId(), first.getWorkOrderOperationId(),
                new OperationInputRequest(0L, BigDecimal.TEN), null), BusinessErrorCode.MATERIAL_ISSUE_REQUIRED);
        verify(inputRepository, never()).save(any());
    }

    /** FE Q14: the cap is the plan, never derived from the material issued. 7 confirmed of 10 planned ⇒ 4 is over. */
    @Test
    void input_firstOperationOverThePlan_isPlannedQuantityExceededOnQuantity() {
        WorkOrderOperation first = operation(10);
        stubLedger(List.of(first), Map.of(first.getWorkOrderOperationId(), "7"), Map.of(), List.of());
        when(issueLineRepository.sumIssuedQuantityByWorkOrder(workOrder.getWorkOrderId())).thenReturn(new BigDecimal("500"));

        assertThatThrownBy(() -> service.recordInput(workOrder.getWorkOrderId(), first.getWorkOrderOperationId(),
                new OperationInputRequest(0L, new BigDecimal("4")), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("quantity");
                });
        verify(inputRepository, never()).save(any());
    }

    @Test
    void input_firstOperationWithinThePlan_isRecordedFromThePlanAndMovesTheVersionOnce() {
        workOrder.setStatus(WorkOrderStatus.RELEASED);
        WorkOrderOperation first = operation(10);
        stubLedger(List.of(first), Map.of(first.getWorkOrderOperationId(), "7"), Map.of(), List.of());
        when(issueLineRepository.sumIssuedQuantityByWorkOrder(workOrder.getWorkOrderId())).thenReturn(BigDecimal.ONE);

        service.recordInput(workOrder.getWorkOrderId(), first.getWorkOrderOperationId(),
                new OperationInputRequest(0L, new BigDecimal("3")), null);

        ArgumentCaptor<WorkOrderOperationInput> saved = ArgumentCaptor.forClass(WorkOrderOperationInput.class);
        verify(inputRepository).save(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualByComparingTo("3");
        assertThat(saved.getValue().getSourceOperationId()).isNull();
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(workOrderRepository, times(1)).saveAndFlush(workOrder);
    }

    @Test
    void input_laterOperationBeforeThePreviousIsFinished_isAnOperationSequenceConflict() {
        WorkOrderOperation first = operation(10);
        WorkOrderOperation second = operation(20);
        stubLedger(List.of(first, second), Map.of(), Map.of(), List.of(totals(first, "5", "0", "0", "5", "0", 1)));

        assertCode(() -> service.recordInput(workOrder.getWorkOrderId(), second.getWorkOrderOperationId(),
                new OperationInputRequest(0L, BigDecimal.ONE), null), BusinessErrorCode.OPERATION_SEQUENCE_CONFLICT);
    }

    /** FE Q15: a later operation takes only good its finished predecessor has not transferred yet (9 − 6 = 3). */
    @Test
    void input_laterOperationOverWhatIsLeftToTransfer_isPlannedQuantityExceeded() {
        WorkOrderOperation first = operation(10);
        first.finish(Instant.now(), null);
        WorkOrderOperation second = operation(20);
        stubLedger(List.of(first, second), Map.of(), Map.of(first.getWorkOrderOperationId(), "6"),
                List.of(totals(first, "9", "1", "0", "10", "0", 1)));

        assertCode(() -> service.recordInput(workOrder.getWorkOrderId(), second.getWorkOrderOperationId(),
                new OperationInputRequest(0L, new BigDecimal("4")), null), BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
        verify(inputRepository, never()).save(any());
    }

    @Test
    void input_laterOperationWithinWhatIsLeft_isATransferFromThePredecessor() {
        WorkOrderOperation first = operation(10);
        first.finish(Instant.now(), null);
        WorkOrderOperation second = operation(20);
        stubLedger(List.of(first, second), Map.of(), Map.of(first.getWorkOrderOperationId(), "6"),
                List.of(totals(first, "9", "1", "0", "10", "0", 1)));

        service.recordInput(workOrder.getWorkOrderId(), second.getWorkOrderOperationId(),
                new OperationInputRequest(0L, new BigDecimal("3")), null);

        ArgumentCaptor<WorkOrderOperationInput> saved = ArgumentCaptor.forClass(WorkOrderOperationInput.class);
        verify(inputRepository).save(saved.capture());
        assertThat(saved.getValue().getSourceOperationId()).isEqualTo(first.getWorkOrderOperationId());
        assertThat(saved.getValue().getOperation()).isSameAs(second);
    }

    @Test
    void input_onAFinishedOperation_isAStateConflict() {
        WorkOrderOperation first = operation(10);
        first.finish(Instant.now(), null);
        stubLedger(List.of(first), Map.of(), Map.of(), List.of());

        assertCode(() -> service.recordInput(workOrder.getWorkOrderId(), first.getWorkOrderOperationId(),
                new OperationInputRequest(0L, BigDecimal.ONE), null), BusinessErrorCode.STATE_CONFLICT);
    }

    @Test
    void input_onAWorkOrderWithoutInputEvidence_isProductionInputUnknown() {
        workOrder.setOperationTracking(false);

        assertCode(() -> service.recordInput(workOrder.getWorkOrderId(), UUID.randomUUID(),
                new OperationInputRequest(0L, BigDecimal.ONE), null), BusinessErrorCode.PRODUCTION_INPUT_UNKNOWN);
        verify(inputRepository, never()).save(any());
    }

    @Test
    void input_staleVersion_isConcurrentModificationBeforeAnyRead() {
        workOrder.setVersion(4L);

        assertCode(() -> service.recordInput(workOrder.getWorkOrderId(), UUID.randomUUID(),
                new OperationInputRequest(3L, BigDecimal.ONE), null), BusinessErrorCode.CONCURRENT_MODIFICATION);
        verify(operationRepository, never()).findByWorkOrderWorkOrderIdOrderBySequenceAsc(any());
    }

    /** A retried input with the same key answers with the operation as it is now, without recording again. */
    @Test
    void input_replayedKey_returnsTheOperationWithoutRecordingAgain() {
        workOrder.setVersion(5L);
        WorkOrderOperation first = operation(10);
        stubLedger(List.of(first), Map.of(first.getWorkOrderOperationId(), "3"), Map.of(), List.of());
        OperationInputRequest request = new OperationInputRequest(4L, new BigDecimal("3"));
        String hash = new IdempotencySupport(new ObjectMapper().findAndRegisterModules()).payloadHash(
                new WorkOrderIdempotency.Fingerprint(workOrder.getWorkOrderId(),
                        new OperationCommandProbe(first.getWorkOrderOperationId(), request)));
        when(idempotencyRecordRepository.findByIdempotencyKeyAndCommand("IN-1", WorkOrderCommand.OPERATION_INPUT))
                .thenReturn(Optional.of(WorkOrderIdempotencyRecord.builder()
                        .idempotencyKey("IN-1").resourceId(workOrder.getWorkOrderId()).payloadHash(hash).build()));

        var row = service.recordInput(workOrder.getWorkOrderId(), first.getWorkOrderOperationId(), request, "IN-1");

        assertThat(row.inputQuantity()).isEqualByComparingTo("3");
        assertThat(row.workOrderVersion()).isEqualTo(5L);
        verify(inputRepository, never()).save(any());
        verify(workOrderRepository, never()).saveAndFlush(any());
    }

    // ── finish ──────────────────────────────────────────────────────────────────────────────

    @Test
    void finish_withConfirmedInputNotYetProcessed_isAStateConflict() {
        WorkOrderOperation first = operation(10);
        stubLedger(List.of(first), Map.of(first.getWorkOrderOperationId(), "10"), Map.of(),
                List.of(totals(first, "7", "0", "0", "7", "0", 1)));

        assertCode(() -> service.finish(workOrder.getWorkOrderId(), first.getWorkOrderOperationId(),
                new WorkOrderVersionRequest(0L), null), BusinessErrorCode.STATE_CONFLICT);
        assertThat(first.isCompleted()).isFalse();
    }

    @Test
    void finish_withReworkStillPending_isAStateConflict() {
        WorkOrderOperation first = operation(10);
        stubLedger(List.of(first), Map.of(first.getWorkOrderOperationId(), "10"), Map.of(),
                List.of(totals(first, "8", "0", "2", "10", "1", 2)));

        assertCode(() -> service.finish(workOrder.getWorkOrderId(), first.getWorkOrderOperationId(),
                new WorkOrderVersionRequest(0L), null), BusinessErrorCode.STATE_CONFLICT);
        assertThat(first.isCompleted()).isFalse();
    }

    /**
     * B218: pending rework is rework minus what retries processed. 2 recorded, 2 processed again ⇒ nothing pending, so
     * the operation finishes — reading pending as the raw rework total would keep it stuck forever.
     */
    @Test
    void finish_afterRetriesProcessedEveryRework_isAllowed() {
        WorkOrderOperation first = operation(10);
        stubLedger(List.of(first), Map.of(first.getWorkOrderOperationId(), "10"), Map.of(),
                List.of(totals(first, "10", "0", "2", "10", "2", 3)));

        var row = service.finish(workOrder.getWorkOrderId(), first.getWorkOrderOperationId(),
                new WorkOrderVersionRequest(0L), null);

        assertThat(row.pendingReworkQuantity()).isEqualByComparingTo("0");
        assertThat(row.historicalReworkQuantity()).isEqualByComparingTo("2");
        assertThat(first.isCompleted()).isTrue();
    }

    /** FE A25: 7 of 10 confirmed and all of it processed — the first operation still cannot finish short of the plan. */
    @Test
    void finish_firstOperationWithLessInputThanThePlan_isAStateConflict() {
        WorkOrderOperation first = operation(10);
        stubLedger(List.of(first), Map.of(first.getWorkOrderOperationId(), "7"), Map.of(),
                List.of(totals(first, "7", "0", "0", "7", "0", 1)));

        assertCode(() -> service.finish(workOrder.getWorkOrderId(), first.getWorkOrderOperationId(),
                new WorkOrderVersionRequest(0L), null), BusinessErrorCode.STATE_CONFLICT);
        assertThat(first.isCompleted()).isFalse();
    }

    @Test
    void finish_withPredecessorGoodNotYetTransferred_isAStateConflict() {
        WorkOrderOperation first = operation(10);
        first.finish(Instant.now(), null);
        WorkOrderOperation second = operation(20);
        stubLedger(List.of(first, second), Map.of(second.getWorkOrderOperationId(), "6"),
                Map.of(first.getWorkOrderOperationId(), "6"),
                List.of(totals(first, "9", "1", "0", "10", "0", 1), totals(second, "6", "0", "0", "6", "0", 1)));

        assertCode(() -> service.finish(workOrder.getWorkOrderId(), second.getWorkOrderOperationId(),
                new WorkOrderVersionRequest(0L), null), BusinessErrorCode.STATE_CONFLICT);
    }

    @Test
    void finish_beforeThePreviousOperation_isAnOperationSequenceConflict() {
        WorkOrderOperation first = operation(10);
        WorkOrderOperation second = operation(20);
        stubLedger(List.of(first, second), Map.of(), Map.of(), List.of());

        assertCode(() -> service.finish(workOrder.getWorkOrderId(), second.getWorkOrderOperationId(),
                new WorkOrderVersionRequest(0L), null), BusinessErrorCode.OPERATION_SEQUENCE_CONFLICT);
    }

    /** FE Q16: the predecessor finished with no good — the next operation finishes with nothing, so all-scrap is not stuck. */
    @Test
    void finish_afterAPredecessorThatMadeNoGood_isAllowedWithNoInput() {
        WorkOrderOperation first = operation(10);
        first.finish(Instant.now(), null);
        WorkOrderOperation second = operation(20);
        stubLedger(List.of(first, second), Map.of(), Map.of(), List.of(totals(first, "0", "10", "0", "10", "0", 1)));

        var row = service.finish(workOrder.getWorkOrderId(), second.getWorkOrderOperationId(),
                new WorkOrderVersionRequest(0L), null);

        assertThat(second.isCompleted()).isTrue();
        assertThat(row.status()).isEqualTo("COMPLETED");
        verify(workOrderRepository, times(1)).saveAndFlush(workOrder);
    }

    /** The last good unit completes the work order (B53) before its last operation is finished — finish still works. */
    @Test
    void finish_onACompletedWorkOrder_isAllowed() {
        workOrder.setStatus(WorkOrderStatus.COMPLETED);
        WorkOrderOperation only = operation(10);
        stubLedger(List.of(only), Map.of(only.getWorkOrderOperationId(), "10"), Map.of(),
                List.of(totals(only, "10", "0", "0", "10", "0", 1)));

        service.finish(workOrder.getWorkOrderId(), only.getWorkOrderOperationId(), new WorkOrderVersionRequest(0L), null);

        assertThat(only.isCompleted()).isTrue();
    }

    // ── complete-production ─────────────────────────────────────────────────────────────────

    @Test
    void complete_withAnOperationNotFinished_isAStateConflict() {
        WorkOrderOperation only = operation(10);
        stubLedger(List.of(only), Map.of(), Map.of(), List.of());

        assertCode(() -> service.completeProduction(workOrder.getWorkOrderId(),
                new ProductionCompletionRequest(0L, "Steel ran out"), null), BusinessErrorCode.STATE_CONFLICT);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    }

    @Test
    void complete_withAnIssuePendingApproval_isAStateConflict() {
        when(issueRepository.existsByWorkOrderWorkOrderIdAndStatus(workOrder.getWorkOrderId(),
                MaterialIssueStatus.PENDING_APPROVAL)).thenReturn(true);

        assertCode(() -> service.completeProduction(workOrder.getWorkOrderId(),
                new ProductionCompletionRequest(0L, "Steel ran out"), null), BusinessErrorCode.STATE_CONFLICT);
    }

    @Test
    void complete_belowThePlanWithoutAReason_isApprovalReasonRequired() {
        finishedSingleOperation("6");

        assertThatThrownBy(() -> service.completeProduction(workOrder.getWorkOrderId(),
                new ProductionCompletionRequest(0L, null), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.APPROVAL_REASON_REQUIRED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("reason");
                });
    }

    /** FE Q4: completing below the plan also needs PERM_MATERIAL_ISSUE_APPROVE. */
    @Test
    void complete_belowThePlanWithoutApprovePermission_isAccessDenied() {
        finishedSingleOperation("6");
        when(permissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_APPROVE"), eq(workOrder.getWorkOrderId())))
                .thenReturn(false);

        assertCode(() -> service.completeProduction(workOrder.getWorkOrderId(),
                new ProductionCompletionRequest(0L, "Steel ran out"), null), AuthErrorCode.ACCESS_DENIED);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    }

    @Test
    void complete_belowThePlanWithReasonAndApproval_capturesTheShortfallOnce() {
        finishedSingleOperation("6");
        when(permissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_APPROVE"), eq(workOrder.getWorkOrderId())))
                .thenReturn(true);
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        var response = service.completeProduction(workOrder.getWorkOrderId(),
                new ProductionCompletionRequest(0L, "  Steel ran out  "), null);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.terminalShortfallQuantity()).isEqualByComparingTo("4");
        assertThat(response.shortfallReason()).isEqualTo("Steel ran out");
        assertThat(response.finalGoodQuantity()).isEqualByComparingTo("6");
        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("0");
        verify(workOrderRepository, times(1)).saveAndFlush(workOrder);
    }

    @Test
    void complete_onACompletedWorkOrder_isAStateConflict() {
        workOrder.setStatus(WorkOrderStatus.COMPLETED);

        assertCode(() -> service.completeProduction(workOrder.getWorkOrderId(),
                new ProductionCompletionRequest(0L, "Steel ran out"), null), BusinessErrorCode.STATE_CONFLICT);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    private void finishedSingleOperation(String finalGood) {
        workOrder.setActualGoodQuantity(new BigDecimal(finalGood));
        WorkOrderOperation only = operation(10);
        only.finish(Instant.now(), null);
        stubLedger(List.of(only), Map.of(only.getWorkOrderOperationId(), finalGood), Map.of(),
                List.of(totals(only, finalGood, "0", "0", finalGood, "0", 1)));
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }

    private void stubLedger(List<WorkOrderOperation> operations, Map<UUID, String> inputs,
                            Map<UUID, String> transferred, List<OperationExecutionTotals> totals) {
        UUID id = workOrder.getWorkOrderId();
        lenient().when(operationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(id)).thenReturn(operations);
        lenient().when(inputRepository.sumInputByOperation(id)).thenReturn(inputs.entrySet().stream()
                .map(e -> ProductionExecutionServiceTest.keyed(e.getKey(), e.getValue())).toList());
        lenient().when(inputRepository.sumTransferredBySourceOperation(id)).thenReturn(transferred.entrySet().stream()
                .map(e -> ProductionExecutionServiceTest.keyed(e.getKey(), e.getValue())).toList());
        lenient().when(executionRepository.sumByOperation(id)).thenReturn(totals);
    }

    private static OperationExecutionTotals totals(WorkOrderOperation operation, String good, String scrap, String rework,
                                                   String fresh, String retry, long count) {
        return ProductionExecutionServiceTest.totals(operation, good, scrap, rework, fresh, retry, count);
    }

    private WorkOrderOperation operation(int sequence) {
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

    private static WorkOrder workOrder(WorkOrderStatus status, BigDecimal planned) {
        Company company = Company.builder().companyId(UUID.randomUUID()).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P1").name("Plant 1")
                .status(OrganizationStatus.ACTIVE).build();
        Item product = Item.builder().itemId(UUID.randomUUID()).company(company).code("FG-100").name("FG-100")
                .type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).build();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .workOrderNo("WO-001")
                .productItem(product)
                .plannedQuantity(planned)
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

    /** Same shape as the service's private fingerprint record, so the hash matches what the service stored. */
    private record OperationCommandProbe(UUID operationId, Object body) {}
}
