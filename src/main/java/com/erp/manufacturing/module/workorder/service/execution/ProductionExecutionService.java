package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionCandidateResponse;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionPostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Production Execution — the shop-floor report (spec §5).
 * <p>
 * This service carries the semantic inversion of F5 (CLAUDE.md §0.5): <b>this</b> is what makes a
 * work order progress and complete. The production receipt no longer does; it only moves finished
 * output into stock, and can never claim more than what was reported here (invariant B16).
 */
@Service
@RequiredArgsConstructor
public class ProductionExecutionService {

    /**
     * Statuses the shop floor may report against — the same gate {@code canExecute()} enforces on the
     * write path (B13), spelled out here because a query cannot call an entity method.
     */
    private static final List<WorkOrderStatus> EXECUTION_CANDIDATE_STATUSES =
            List.of(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS);

    private final ProductionExecutionRepository executionRepository;
    private final WorkOrderOperationRepository operationRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WipTransactionService wipTransactionService;
    private final WorkOrderExecutionSupport support;
    private final ManufacturingExecutionMapper mapper;
    private final IdempotencySupport idempotency;
    private final TraceIdProvider traceIdProvider;
    private final SecurityAuditorAware auditorAware;
    private final UserLookupService userLookupService;
    private final WorkOrderCostAccumulatorService costAccumulatorService;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_EXECUTION_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_EXECUTION_REPORTED, entityType = "ProductionExecution",
            entityIdExpression = "productionExecutionId.toString()")
    public ProductionExecutionResponse report(UUID workOrderId,
                                              ProductionExecutionPostRequest request,
                                              String idempotencyKey) {
        String normalizedKey = idempotency.normalizeKey(idempotencyKey);
        return executionRepository.findWithDetailsByIdempotencyKey(normalizedKey)
                .map(existing -> {
                    idempotency.ensureSamePayload(existing.getPayloadHash(), request);
                    return toResponse(existing);
                })
                .orElseGet(() -> reportNew(workOrderId, request, normalizedKey));
    }

    /**
     * The work orders this plant may still report production against (spec §5.1).
     *
     * <p>Scoped by plant rather than by a single work order, so it authorises on the plant the way
     * {@code WorkOrderService.list} does — {@code hasWorkOrderAccess} needs an id that does not exist
     * yet at this point. The permission itself is unchanged ({@code PERM_PRODUCTION_EXECUTION_READ}),
     * so no new permission and no seed migration.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_EXECUTION_READ', 'PLANT', #plantId)")
    public PageResult<ProductionExecutionCandidateResponse> listCandidates(UUID plantId, Pageable pageable) {
        return PageResult.from(workOrderRepository
                .findExecutionCandidates(plantId, EXECUTION_CANDIDATE_STATUSES, pageable)
                .map(mapper::toCandidateResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_EXECUTION_READ', #workOrderId)")
    public PageResult<ProductionExecutionResponse> list(UUID workOrderId, Pageable pageable) {
        support.findWorkOrder(workOrderId);
        Page<ProductionExecution> page = executionRepository.findByWorkOrderWorkOrderId(workOrderId, pageable);
        Map<UUID, String> usernames = usernamesOf(page.getContent());
        return PageResult.from(page.map(execution -> mapper.toResponse(execution, usernames)));
    }

    /** Resolves the operator username of a single execution in one query. */
    private ProductionExecutionResponse toResponse(ProductionExecution execution) {
        return mapper.toResponse(execution, usernamesOf(List.of(execution)));
    }

    /** One query for the whole page (rule C14) — never one lookup per row. */
    private Map<UUID, String> usernamesOf(List<ProductionExecution> executions) {
        return userLookupService.findUsernames(executions.stream()
                .map(ProductionExecution::getOperatorUserId)
                .filter(Objects::nonNull)
                .toList());
    }

    private ProductionExecutionResponse reportNew(UUID workOrderId,
                                                  ProductionExecutionPostRequest request,
                                                  String normalizedKey) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureExecutable(workOrder);

        BigDecimal good = nonNegative(request.goodQuantity(), "Good quantity");
        BigDecimal scrap = nonNegative(request.scrapQuantity(), "Scrap quantity");
        BigDecimal rework = nonNegative(request.reworkQuantity(), "Rework quantity");
        if (good.add(scrap).add(rework).compareTo(BigDecimal.ZERO) == 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    "At least one of good, scrap, or rework quantity must be greater than zero");
        }
        ensureChronological(request);

        // Cumulative good is capped by the plan: over-producing is a planning decision (raise the
        // work order quantity), not something the shop floor may do silently.
        if (good.compareTo(workOrder.remainingPlannedQuantity()) > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                    "Cumulative good quantity would exceed the planned quantity of the work order");
        }

        WorkOrderOperation operation = resolveOperation(workOrderId, request.workOrderOperationId());
        String traceId = traceIdProvider.currentTraceId();
        Instant now = Instant.now();

        ProductionExecution execution = executionRepository.save(ProductionExecution.builder()
                .workOrder(workOrder)
                .operation(operation)
                .goodQuantity(good)
                .scrapQuantity(scrap)
                .reworkQuantity(rework)
                .actualStartedAt(request.actualStartedAt())
                .actualEndedAt(request.actualEndedAt())
                .operatorUserId(auditorAware.getCurrentAuditor().orElse(null))
                .traceId(traceId)
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .notes(support.trimToNull(request.notes()))
                .build());

        // The WIP ledger keeps recording everything, so WorkOrderVarianceService and the shop-floor
        // history stay intact (NEXT_PHASE_PLAN F5 §1.1 item 3).
        recordWip(workOrder, operation, WipTransactionType.OUTPUT_COMPLETED, good, execution);
        recordWip(workOrder, operation, WipTransactionType.SCRAP_REPORTED, scrap, execution);
        recordWip(workOrder, operation, WipTransactionType.REWORK_REPORTED, rework, execution);

        workOrder.reportProduction(good, scrap, rework, now);
        workOrderRepository.save(workOrder);
        if (good.compareTo(BigDecimal.ZERO) > 0) {
            costAccumulatorService.accumulateLaborOverheadCost(workOrder, good);
        }
        return toResponse(execution);
    }

    private void recordWip(WorkOrder workOrder,
                           WorkOrderOperation operation,
                           WipTransactionType type,
                           BigDecimal quantity,
                           ProductionExecution execution) {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        wipTransactionService.recordProductionExecution(
                workOrder, operation, type, quantity, execution.getProductionExecutionId());
    }

    /**
     * The operation must belong to this work order's own snapshot — reporting against a routing
     * operation of a different work order would corrupt the per-operation history.
     */
    private WorkOrderOperation resolveOperation(UUID workOrderId, UUID operationId) {
        if (operationId == null) {
            return null;
        }
        return operationRepository.findByWorkOrderOperationIdAndWorkOrderWorkOrderId(operationId, workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order operation", operationId));
    }

    private void ensureChronological(ProductionExecutionPostRequest request) {
        if (request.actualStartedAt() != null
                && request.actualEndedAt() != null
                && request.actualEndedAt().isBefore(request.actualStartedAt())) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Actual end time cannot be before actual start time");
        }
    }

    private BigDecimal nonNegative(BigDecimal quantity, String fieldName) {
        if (quantity == null) {
            return BigDecimal.ZERO;
        }
        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " cannot be negative");
        }
        return quantity;
    }
}
