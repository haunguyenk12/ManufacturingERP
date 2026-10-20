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
import com.erp.manufacturing.module.workorder.domain.MaterialIssueLine;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.ProductionExecutionConsumption;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.execution.ExecutionConsumptionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ExecutionConsumptionResponse;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionCandidateResponse;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionPostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.KeyedQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionConsumptionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderVersions;
import com.erp.manufacturing.module.workorder.service.execution.OperationLedger.OperationState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** G8c (FE Q31): a tablet's clock may run up to a minute ahead of the server. */
    static final Duration END_TIME_TOLERANCE = Duration.ofSeconds(60);

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
    private final OperationLedger ledger;
    private final ProductionExecutionConsumptionRepository consumptionRepository;
    private final MaterialIssueLineRepository issueLineRepository;
    private final MaterialReturnLineRepository returnLineRepository;

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
        Map<UUID, BigDecimal> retryUsed = retryUsedOf(page.getContent());
        Map<UUID, List<ExecutionConsumptionResponse>> consumption = consumptionOf(page.getContent());
        return PageResult.from(page.map(execution -> mapper.toResponse(execution, usernames, retryUsed, consumption)));
    }

    /** Resolves the operator username, retry use and consumption of a single execution — one query each. */
    private ProductionExecutionResponse toResponse(ProductionExecution execution) {
        List<ProductionExecution> one = List.of(execution);
        return mapper.toResponse(execution, usernamesOf(one), retryUsedOf(one), consumptionOf(one));
    }

    /** One query for the whole page (rule C14) — never one lookup per row. */
    private Map<UUID, String> usernamesOf(List<ProductionExecution> executions) {
        return userLookupService.findUsernames(executions.stream()
                .map(ProductionExecution::getOperatorUserId)
                .filter(Objects::nonNull)
                .toList());
    }

    /** G8c: how much of each execution's rework later retries processed — one query for the page (C14). */
    private Map<UUID, BigDecimal> retryUsedOf(List<ProductionExecution> executions) {
        List<UUID> ids = idsOf(executions);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return executionRepository.sumRetryProcessedBySource(ids).stream()
                .collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
    }

    /** G8c: recorded consumption of each execution — one query for the page (C14). */
    private Map<UUID, List<ExecutionConsumptionResponse>> consumptionOf(List<ProductionExecution> executions) {
        List<UUID> ids = idsOf(executions);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return consumptionRepository.findByProductionExecutionProductionExecutionIdIn(ids).stream()
                .collect(Collectors.groupingBy(row -> row.getProductionExecution().getProductionExecutionId(),
                        Collectors.mapping(row -> new ExecutionConsumptionResponse(row.getIssueLineId(), row.getQuantity()),
                                Collectors.toList())));
    }

    private static List<UUID> idsOf(List<ProductionExecution> executions) {
        return executions.stream().map(ProductionExecution::getProductionExecutionId).filter(Objects::nonNull).toList();
    }

    /**
     * G8c rules (FE R10–R13, D4, D7), on top of the F5 ones:
     * <ul>
     *   <li>{@code expectedWorkOrderVersion} is required (D5); a replay was already answered by {@link #report}.</li>
     *   <li>A work order with operations reports against one of them, whose predecessor must be finished.</li>
     *   <li>Fresh work uses the operation's confirmed input ({@code input − fresh processed}); a retry uses its
     *       source's pending rework — two pools, never one (B218).</li>
     *   <li>Only the last operation's good is the work order's final good (B219).</li>
     *   <li>Consumption draws on the work order's own posted issue lines, duplicates added up first (B220).</li>
     *   <li>A work order that had production reported before V81 keeps the F5 rules and accepts neither a retry
     *       nor consumption: its inputs were never recorded (FE Q22).</li>
     * </ul>
     * Every change to the work order is made and {@link WorkOrder#touch() touched} before the first write, and it is
     * flushed once at the end, so a report moves it on exactly one version (B212).
     */
    private ProductionExecutionResponse reportNew(UUID workOrderId,
                                                  ProductionExecutionPostRequest request,
                                                  String normalizedKey) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request.expectedWorkOrderVersion(), "expectedWorkOrderVersion");
        support.ensureExecutable(workOrder);

        BigDecimal good = nonNegative(request.goodQuantity(), "Good quantity");
        BigDecimal scrap = nonNegative(request.scrapQuantity(), "Scrap quantity");
        BigDecimal rework = nonNegative(request.reworkQuantity(), "Rework quantity");
        BigDecimal processed = good.add(scrap).add(rework);
        if (processed.compareTo(BigDecimal.ZERO) == 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    "At least one of good, scrap, or rework quantity must be greater than zero");
        }
        ensureChronological(request);

        List<ExecutionConsumptionRequest> consumption = request.consumptionOrEmpty();
        WorkOrderOperation operation;
        boolean countsAsFinalGood = true;
        if (!workOrder.isOperationTracking()) {
            if (request.sourceExecutionId() != null || !consumption.isEmpty()) {
                throw ExceptionFactory.custom(BusinessErrorCode.PRODUCTION_INPUT_UNKNOWN,
                        "Production of this work order was reported before inputs and consumption were recorded");
            }
            operation = resolveOperation(workOrderId, request.workOrderOperationId());
            ensureWithinPlan(workOrder, good);
        } else {
            List<OperationState> states = ledger.load(workOrderId);
            if (states.isEmpty()) {
                operation = resolveOperation(workOrderId, request.workOrderOperationId());
                ensureWithinPlan(workOrder, good);
                if (request.sourceExecutionId() != null) {
                    ensureWithinSource(workOrderId, null, request.sourceExecutionId(), processed);
                }
            } else {
                if (request.workOrderOperationId() == null) {
                    throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                            Map.of("workOrderOperationId", "is required on a work order with operations"));
                }
                OperationState state = OperationLedger.find(states, request.workOrderOperationId());
                operation = state.operation();
                if (operation.isCompleted()) {
                    throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, "The operation is already finished");
                }
                if (!state.predecessorCompleted()) {
                    throw ExceptionFactory.custom(BusinessErrorCode.OPERATION_SEQUENCE_CONFLICT,
                            "The previous operation must be finished before this one reports production");
                }
                if (request.sourceExecutionId() == null) {
                    if (processed.compareTo(state.freshRemaining()) > 0) {
                        throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                                Map.of("goodQuantity", "good + scrap + rework exceeds the "
                                        + state.freshRemaining().toPlainString() + " confirmed input left"));
                    }
                } else {
                    ensureWithinSource(workOrderId, operation.getWorkOrderOperationId(),
                            request.sourceExecutionId(), processed);
                }
                countsAsFinalGood = state.last();
            }
        }
        List<ProductionExecutionConsumption> consumptionRows = validateConsumption(workOrderId, consumption);

        String traceId = traceIdProvider.currentTraceId();
        Instant now = Instant.now();
        workOrder.reportProduction(good, scrap, rework, countsAsFinalGood, now);
        workOrder.touch();

        ProductionExecution execution = executionRepository.save(ProductionExecution.builder()
                .workOrder(workOrder)
                .operation(operation)
                .sourceExecutionId(request.sourceExecutionId())
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
        consumptionRows.forEach(row -> row.setProductionExecution(execution));
        consumptionRepository.saveAll(consumptionRows);

        // The WIP ledger keeps recording everything, so WorkOrderVarianceService and the shop-floor
        // history stay intact (NEXT_PHASE_PLAN F5 §1.1 item 3).
        recordWip(workOrder, operation, WipTransactionType.OUTPUT_COMPLETED, good, execution);
        recordWip(workOrder, operation, WipTransactionType.SCRAP_REPORTED, scrap, execution);
        recordWip(workOrder, operation, WipTransactionType.REWORK_REPORTED, rework, execution);

        // Labour and overhead are absorbed per finished unit — and only the final good is a finished unit (B219).
        if (countsAsFinalGood && good.compareTo(BigDecimal.ZERO) > 0) {
            costAccumulatorService.accumulateLaborOverheadCost(workOrder, good);
        }
        support.flushTouched(workOrder);
        return toResponse(execution);
    }

    /** Cumulative final good is capped by the plan (B54): over-producing is a planning decision. */
    private static void ensureWithinPlan(WorkOrder workOrder, BigDecimal good) {
        if (good.compareTo(workOrder.remainingPlannedQuantity()) > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                    "Cumulative good quantity would exceed the planned quantity of the work order");
        }
    }

    /**
     * A retry processes rework of an earlier execution of the same work order and operation (FE R11, D7), and at
     * most what that execution still has pending: its rework minus everything already recorded against it.
     */
    private void ensureWithinSource(UUID workOrderId, UUID operationId, UUID sourceExecutionId, BigDecimal processed) {
        ProductionExecution source = executionRepository.findById(sourceExecutionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Production execution", sourceExecutionId));
        UUID sourceOperationId = source.getOperation() == null ? null : source.getOperation().getWorkOrderOperationId();
        if (!source.getWorkOrder().getWorkOrderId().equals(workOrderId) || !Objects.equals(sourceOperationId, operationId)) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    Map.of("sourceExecutionId", "must be an execution of the same work order and operation"));
        }
        BigDecimal used = executionRepository.sumRetryProcessedBySource(List.of(sourceExecutionId)).stream()
                .map(KeyedQuantityProjection::getQuantity)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        BigDecimal pending = source.getReworkQuantity().subtract(used);
        if (processed.compareTo(pending) > 0) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                    Map.of("goodQuantity", "good + scrap + rework exceeds the " + pending.toPlainString()
                            + " rework still pending on the source execution"));
        }
    }

    /**
     * FE R13: each line must be a posted issue line of this work order, and what is recorded against it — this
     * execution's entries for the line added up first — may not pass {@code issued − consumed} (returns come with
     * G8d). Nothing is posted to stock: the material left the warehouse when it was issued.
     */
    private List<ProductionExecutionConsumption> validateConsumption(UUID workOrderId,
                                                                    List<ExecutionConsumptionRequest> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> requested = new LinkedHashMap<>();
        Map<UUID, Integer> firstIndex = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            ExecutionConsumptionRequest entry = entries.get(i);
            requested.merge(entry.issueLineId(), entry.quantity(), BigDecimal::add);
            firstIndex.putIfAbsent(entry.issueLineId(), i);
        }
        Map<UUID, MaterialIssueLine> lines = issueLineRepository.findWithIssueByIssueLineIdIn(requested.keySet()).stream()
                .collect(Collectors.toMap(MaterialIssueLine::getIssueLineId, Function.identity()));
        Map<UUID, BigDecimal> consumed = consumptionRepository.sumByIssueLineIds(requested.keySet()).stream()
                .collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
        Map<UUID, BigDecimal> returned = returnLineRepository.sumBySourceIssueLineIds(requested.keySet()).stream()
                .collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
        List<ProductionExecutionConsumption> rows = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : requested.entrySet()) {
            int index = firstIndex.get(entry.getKey());
            MaterialIssueLine line = lines.get(entry.getKey());
            if (line == null || !line.getIssue().getWorkOrder().getWorkOrderId().equals(workOrderId)) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                        Map.of("consumption[" + index + "].issueLineId", "must be an issue line of this work order"));
            }
            if (line.getIssue().getStatus() != MaterialIssueStatus.POSTED) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.STATE_CONFLICT,
                        Map.of("consumption[" + index + "].issueLineId", "the material issue is not posted"));
            }
            // G8d: material returned to the warehouse is no longer at the line (FE §1.4 step 9).
            BigDecimal left = line.getQuantity()
                    .subtract(consumed.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                    .subtract(returned.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            if (entry.getValue().compareTo(left) > 0) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                        Map.of("consumption[" + index + "].quantity",
                                "only " + left.toPlainString() + " of this issue line is left unconsumed"));
            }
            rows.add(ProductionExecutionConsumption.builder()
                    .issueLineId(entry.getKey())
                    .quantity(entry.getValue())
                    .build());
        }
        return rows;
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
        // G8c (FE Q31): an end time in the future is a typing or clock error, past a minute of tablet clock skew.
        if (request.actualEndedAt() != null
                && request.actualEndedAt().isAfter(Instant.now().plus(END_TIME_TOLERANCE))) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("actualEndedAt", "cannot be more than 60 seconds after the server time"));
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
