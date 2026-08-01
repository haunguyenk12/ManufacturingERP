package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.workorder.domain.WipTransaction;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.execution.WipTransactionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.WipTransactionResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.WipTransactionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WipTransactionService {

    /** Reference type of WIP rows raised by a Production Execution report (F5). */
    static final String PRODUCTION_EXECUTION_REFERENCE_TYPE = "PRODUCTION_EXECUTION";

    private final WipTransactionRepository wipTransactionRepository;
    private final WorkOrderOperationRepository operationRepository;
    private final WorkOrderExecutionSupport support;
    private final ManufacturingExecutionMapper mapper;

    /**
     * Operations belong to exactly one work order's snapshot; reporting WIP against another work
     * order's operation would silently corrupt the per-operation history.
     */
    private WorkOrderOperation resolveOperation(UUID workOrderId, UUID operationId) {
        if (operationId == null) {
            return null;
        }
        return operationRepository.findByWorkOrderOperationIdAndWorkOrderWorkOrderId(operationId, workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order operation", operationId));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WIP_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WIP_TRANSACTION_RECORDED, entityType = "WipTransaction", entityIdExpression = "wipTransactionId.toString()")
    public WipTransactionResponse record(UUID workOrderId, WipTransactionRequest request) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureNotClosedForWip(workOrder);
        if (request.transactionType() != WipTransactionType.SCRAP_REPORTED
                && request.transactionType() != WipTransactionType.REWORK_REPORTED) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only scrap and rework can be reported manually");
        }
        BigDecimal quantity = support.requirePositive(request.quantity(), "WIP transaction quantity");
        WorkOrderOperation operation = resolveOperation(workOrderId, request.workOrderOperationId());
        WipTransaction transaction = build(
                workOrder,
                request.transactionType(),
                operation,
                operation != null ? operation.stageCode() : request.stageCode(),
                quantity,
                null,
                null,
                request.occurredAt() != null ? request.occurredAt() : Instant.now(),
                support.trimToNull(request.note()));
        return mapper.toResponse(wipTransactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WIP_MANAGE', #workOrderId)")
    public PageResult<WipTransactionResponse> list(UUID workOrderId, Pageable pageable) {
        support.findWorkOrder(workOrderId);
        return PageResult.from(wipTransactionRepository.findByWorkOrderWorkOrderId(workOrderId, pageable)
                .map(mapper::toResponse));
    }

    @Transactional
    public void recordStart(WorkOrder workOrder) {
        wipTransactionRepository.save(build(
                workOrder,
                WipTransactionType.START,
                null,
                null,
                null,
                WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE,
                workOrder.getWorkOrderId().toString(),
                Instant.now(),
                "Work order released"));
    }

    @Transactional
    public void recordMaterialIssued(WorkOrder workOrder, BigDecimal quantity, UUID issueId) {
        wipTransactionRepository.save(build(
                workOrder,
                WipTransactionType.MATERIAL_ISSUED,
                null,
                null,
                quantity,
                "MATERIAL_ISSUE",
                issueId.toString(),
                Instant.now(),
                null));
    }

    /**
     * Warehousing of finished output. Since F5 this is a <em>different</em> event from
     * {@code OUTPUT_COMPLETED}, which the shop floor raises when it actually produces the goods.
     */
    @Transactional
    public void recordOutputReceipted(WorkOrder workOrder, BigDecimal quantity, UUID receiptId) {
        wipTransactionRepository.save(build(
                workOrder,
                WipTransactionType.OUTPUT_RECEIPTED,
                null,
                null,
                quantity,
                "PRODUCTION_RECEIPT",
                receiptId.toString(),
                Instant.now(),
                null));
    }

    /**
     * One WIP row per reported quantity of a Production Execution (F5). When the caller reported
     * against an operation, the ledger row is linked to it and {@code stageCode} is derived from
     * the operation rather than being free text.
     */
    @Transactional
    public WipTransaction recordProductionExecution(WorkOrder workOrder,
                                                    WorkOrderOperation operation,
                                                    WipTransactionType type,
                                                    BigDecimal quantity,
                                                    UUID executionId) {
        WipTransaction transaction = build(
                workOrder,
                type,
                operation,
                operation == null ? null : operation.stageCode(),
                quantity,
                PRODUCTION_EXECUTION_REFERENCE_TYPE,
                executionId.toString(),
                Instant.now(),
                null);
        return wipTransactionRepository.save(transaction);
    }

    private WipTransaction build(WorkOrder workOrder,
                                 WipTransactionType type,
                                 WorkOrderOperation operation,
                                 String stageCode,
                                 BigDecimal quantity,
                                 String referenceType,
                                 String referenceId,
                                 Instant occurredAt,
                                 String note) {
        return WipTransaction.builder()
                .workOrder(workOrder)
                .transactionType(type)
                .operation(operation)
                .stageCode(support.trimToNull(stageCode))
                .quantity(quantity)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .occurredAt(occurredAt)
                .note(note)
                .build();
    }
}
