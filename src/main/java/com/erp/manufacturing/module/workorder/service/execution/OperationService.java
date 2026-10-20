package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCommand;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperationInput;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest;
import com.erp.manufacturing.module.workorder.dto.operation.OperationInputRequest;
import com.erp.manufacturing.module.workorder.dto.operation.ProductionCompletionRequest;
import com.erp.manufacturing.module.workorder.dto.operation.ProductionCompletionResponse;
import com.erp.manufacturing.module.workorder.dto.operation.WorkOrderOperationProgressResponse;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationInputRepository;
import com.erp.manufacturing.module.workorder.service.ReasonRules;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import com.erp.manufacturing.module.workorder.service.WorkOrderVersions;
import com.erp.manufacturing.module.workorder.service.execution.OperationLedger.OperationState;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * G8c (FE R10, R14, D1, D6): the operations of a work order — confirming input, finishing an operation, and
 * completing production by command when the final good is below the plan.
 *
 * <p>Every command here follows the G8 shape (B207): replay the {@code Idempotency-Key} first, then the expected
 * version, then the rules, then claim the key; the work order is {@link WorkOrder#touch() touched} before the first
 * write and flushed once at the end ({@code flushTouched}, B212) so it moves on exactly one version.
 */
@Service
@RequiredArgsConstructor
public class OperationService {

    private static final String SHORTFALL_PERMISSION = "PERM_MATERIAL_ISSUE_APPROVE";

    private final OperationLedger ledger;
    private final WorkOrderExecutionSupport support;
    private final WorkOrderOperationInputRepository inputRepository;
    private final MaterialIssueLineRepository issueLineRepository;
    private final MaterialIssueRepository issueRepository;
    private final WorkOrderIdempotency idempotency;
    private final WorkOrderPermissionGuard permissionGuard;
    private final SecurityAuditorAware auditorAware;

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasAnyWorkOrderAccess(authentication, "
            + "{'PERM_PRODUCTION_EXECUTION_READ', 'PERM_PRODUCTION_EXECUTION_MANAGE'}, #workOrderId)")
    public List<WorkOrderOperationProgressResponse> list(UUID workOrderId) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        return ledger.load(workOrderId).stream()
                .map(state -> OperationLedger.toResponse(state, workOrder))
                .toList();
    }

    /**
     * Confirms input of an operation (FE R10). The first operation takes it from the plan once material was issued
     * (FE Q14 — the cap is the planned quantity, never derived from how much material was issued); a later one takes
     * a transfer of its finished predecessor's good (FE Q15).
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_EXECUTION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.OPERATION_INPUT_RECORDED, entityType = "WorkOrderOperation",
            entityIdExpression = "workOrderOperationId.toString()")
    public WorkOrderOperationProgressResponse recordInput(UUID workOrderId, UUID operationId,
                                                          OperationInputRequest request, String idempotencyKey) {
        String key = idempotency.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint =
                new WorkOrderIdempotency.Fingerprint(workOrderId, new OperationCommand(operationId, request));
        if (idempotency.replay(key, WorkOrderCommand.OPERATION_INPUT, fingerprint).isPresent()) {
            return progress(workOrderId, operationId);
        }
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request.expectedWorkOrderVersion(), "expectedWorkOrderVersion");
        ensureTracked(workOrder);
        support.ensureExecutable(workOrder);
        List<OperationState> states = ledger.load(workOrderId);
        OperationState state = OperationLedger.find(states, operationId);
        if (state.operation().isCompleted()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, "The operation is already finished");
        }
        BigDecimal quantity = request.quantity();
        UUID sourceOperationId = null;
        if (state.first()) {
            if (issueLineRepository.sumIssuedQuantityByWorkOrder(workOrderId).signum() <= 0) {
                throw ExceptionFactory.custom(BusinessErrorCode.MATERIAL_ISSUE_REQUIRED,
                        "Issue material to the work order before confirming the input of its first operation");
            }
            BigDecimal cap = workOrder.getPlannedQuantity().subtract(state.input());
            if (quantity.compareTo(cap) > 0) {
                throw exceeded("quantity", "Input would exceed the planned quantity; at most " + cap.toPlainString() + " more");
            }
        } else {
            if (!state.predecessorCompleted()) {
                throw ExceptionFactory.custom(BusinessErrorCode.OPERATION_SEQUENCE_CONFLICT,
                        "The previous operation must be finished before this one takes input");
            }
            BigDecimal cap = OperationLedger.predecessorOf(states, state).transferable();
            if (quantity.compareTo(cap) > 0) {
                throw exceeded("quantity", "Only " + cap.toPlainString()
                        + " good output of the previous operation is left to transfer");
            }
            sourceOperationId = state.predecessor().getWorkOrderOperationId();
        }
        idempotency.record(key, WorkOrderCommand.OPERATION_INPUT, workOrderId, fingerprint);
        workOrder.markInProgress();
        workOrder.touch();
        inputRepository.save(WorkOrderOperationInput.builder()
                .workOrder(workOrder)
                .operation(state.operation())
                .sourceOperationId(sourceOperationId)
                .quantity(quantity)
                .build());
        support.flushTouched(workOrder);
        return progress(workOrderId, operationId);
    }

    /**
     * Finishes an operation (FE R14): its fresh input and pending rework are used up, and — for a later operation —
     * every good unit of its predecessor was transferred in. The first operation must have taken the whole plan as
     * input (FE A25). An operation whose predecessor finished with no good may finish with nothing (FE Q16), so an
     * all-scrap run never gets stuck.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_EXECUTION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.OPERATION_FINISHED, entityType = "WorkOrderOperation",
            entityIdExpression = "workOrderOperationId.toString()")
    public WorkOrderOperationProgressResponse finish(UUID workOrderId, UUID operationId,
                                                     WorkOrderVersionRequest request, String idempotencyKey) {
        String key = idempotency.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint =
                new WorkOrderIdempotency.Fingerprint(workOrderId, new OperationCommand(operationId, request));
        if (idempotency.replay(key, WorkOrderCommand.OPERATION_FINISH, fingerprint).isPresent()) {
            return progress(workOrderId, operationId);
        }
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request == null ? null : request.expectedWorkOrderVersion(),
                "expectedWorkOrderVersion");
        ensureTracked(workOrder);
        // COMPLETED too: the last good unit completes the work order (B53) before its last operation is finished.
        if (!workOrder.canExecute() && workOrder.getStatus() != WorkOrderStatus.COMPLETED) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Operations can only be finished on a released, in-progress or completed work order");
        }
        List<OperationState> states = ledger.load(workOrderId);
        OperationState state = OperationLedger.find(states, operationId);
        if (state.operation().isCompleted()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, "The operation is already finished");
        }
        if (!state.predecessorCompleted()) {
            throw ExceptionFactory.custom(BusinessErrorCode.OPERATION_SEQUENCE_CONFLICT,
                    "The previous operation must be finished first");
        }
        // FE A25: the first operation takes the whole plan before it finishes — a shortfall is recorded as scrap on the
        // shop floor (A26/A28), never by stopping short on input.
        if (state.first() && state.input().compareTo(workOrder.getPlannedQuantity()) != 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "The first operation must take the whole planned quantity as input before it finishes; "
                            + state.input().toPlainString() + " of " + workOrder.getPlannedQuantity().toPlainString() + " confirmed");
        }
        if (!state.first() && OperationLedger.predecessorOf(states, state).transferable().signum() > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Good output of the previous operation is still waiting to be transferred to this one");
        }
        if (state.freshRemaining().signum() > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    state.freshRemaining().toPlainString() + " confirmed input has not been processed yet");
        }
        if (state.pendingRework().signum() > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    state.pendingRework().toPlainString() + " rework is still waiting to be processed again");
        }
        idempotency.record(key, WorkOrderCommand.OPERATION_FINISH, workOrderId, fingerprint);
        workOrder.touch();
        state.operation().finish(Instant.now().truncatedTo(ChronoUnit.MICROS),
                auditorAware.getCurrentAuditor().orElse(null));
        support.flushTouched(workOrder);
        return progress(workOrderId, operationId);
    }

    /**
     * Completes production by command (FE R14, D6) — the path for finishing below the plan; reaching the plan still
     * completes the work order on its own (B53). Nothing is received, closed or posted to stock, and unused material
     * stays returnable (FE Q23). A shortfall needs a reason and {@code PERM_MATERIAL_ISSUE_APPROVE} (FE Q4).
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_PRODUCTION_COMPLETED, entityType = "WorkOrder",
            entityIdExpression = "workOrderId.toString()")
    public ProductionCompletionResponse completeProduction(UUID workOrderId, ProductionCompletionRequest request,
                                                           String idempotencyKey) {
        String key = idempotency.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        if (idempotency.replay(key, WorkOrderCommand.PRODUCTION_COMPLETE, fingerprint).isPresent()) {
            return toCompletion(support.findWorkOrder(workOrderId));
        }
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request.expectedWorkOrderVersion(), "expectedWorkOrderVersion");
        if (!workOrder.canExecute()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a released or in-progress work order can be completed");
        }
        if (issueRepository.existsByWorkOrderWorkOrderIdAndStatus(workOrderId, MaterialIssueStatus.PENDING_APPROVAL)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "A material issue is still pending approval; approve, reject or cancel it first");
        }
        if (workOrder.isOperationTracking()
                && ledger.load(workOrderId).stream().anyMatch(state -> !state.operation().isCompleted())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Every operation must be finished before production is completed");
        }
        BigDecimal shortfall = workOrder.getPlannedQuantity().subtract(workOrder.getActualGoodQuantity()).max(BigDecimal.ZERO);
        String reason = ReasonRules.lengthChecked(request.reason(), "reason");
        if (shortfall.signum() > 0) {
            if (reason == null) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.APPROVAL_REASON_REQUIRED,
                        Map.of("reason", "is required when the final good is below the plan"));
            }
            if (!permissionGuard.hasWorkOrderAccess(SecurityContextHolder.getContext().getAuthentication(),
                    SHORTFALL_PERMISSION, workOrderId)) {
                throw ExceptionFactory.custom(AuthErrorCode.ACCESS_DENIED,
                        "Completing below the plan also requires " + SHORTFALL_PERMISSION);
            }
        }
        idempotency.record(key, WorkOrderCommand.PRODUCTION_COMPLETE, workOrderId, fingerprint);
        workOrder.completeProduction(shortfall, reason, Instant.now().truncatedTo(ChronoUnit.MICROS));
        workOrder.touch();
        return toCompletion(support.flushTouched(workOrder));
    }

    private WorkOrderOperationProgressResponse progress(UUID workOrderId, UUID operationId) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        return OperationLedger.toResponse(OperationLedger.find(ledger.load(workOrderId), operationId), workOrder);
    }

    private static ProductionCompletionResponse toCompletion(WorkOrder workOrder) {
        return new ProductionCompletionResponse(
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                workOrder.getStatus().name(),
                workOrder.getVersion(),
                workOrder.getPlannedQuantity(),
                workOrder.getActualGoodQuantity(),
                workOrder.getTerminalShortfallQuantity(),
                workOrder.getShortfallReason(),
                workOrder.getCompletedAt());
    }

    private static void ensureTracked(WorkOrder workOrder) {
        if (!workOrder.isOperationTracking()) {
            throw ExceptionFactory.custom(BusinessErrorCode.PRODUCTION_INPUT_UNKNOWN,
                    "Production of this work order was reported before operation inputs were recorded");
        }
    }

    private static RuntimeException exceeded(String field, String message) {
        return ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, Map.of(field, message));
    }

    /** What a key fingerprints for an operation command: the operation and the body. */
    private record OperationCommand(UUID operationId, Object body) {}
}
