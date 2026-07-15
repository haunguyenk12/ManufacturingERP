package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.workorder.domain.WipTransaction;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.dto.execution.WipTransactionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.WipTransactionResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.WipTransactionRepository;
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

    private final WipTransactionRepository wipTransactionRepository;
    private final WorkOrderExecutionSupport support;
    private final ManufacturingExecutionMapper mapper;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WIP_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WIP_TRANSACTION_RECORDED, entityType = "WipTransaction", entityIdExpression = "wipTransactionId.toString()")
    public WipTransactionResponse record(UUID workOrderId, WipTransactionRequest request) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureNotClosedForWip(workOrder);
        if (request.transactionType() != WipTransactionType.SCRAP_REPORTED
                && request.transactionType() != WipTransactionType.REWORK_REPORTED) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only scrap and rework can be reported manually");
        }
        BigDecimal quantity = support.requirePositive(request.quantity(), "WIP transaction quantity");
        WipTransaction transaction = build(
                workOrder,
                request.transactionType(),
                request.stageCode(),
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
                quantity,
                "MATERIAL_ISSUE",
                issueId.toString(),
                Instant.now(),
                null));
    }

    @Transactional
    public void recordOutputCompleted(WorkOrder workOrder, BigDecimal quantity, UUID receiptId) {
        wipTransactionRepository.save(build(
                workOrder,
                WipTransactionType.OUTPUT_COMPLETED,
                null,
                quantity,
                "PRODUCTION_RECEIPT",
                receiptId.toString(),
                Instant.now(),
                null));
    }

    private WipTransaction build(WorkOrder workOrder,
                                 WipTransactionType type,
                                 String stageCode,
                                 BigDecimal quantity,
                                 String referenceType,
                                 String referenceId,
                                 Instant occurredAt,
                                 String note) {
        return WipTransaction.builder()
                .workOrder(workOrder)
                .transactionType(type)
                .stageCode(support.trimToNull(stageCode))
                .quantity(quantity)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .occurredAt(occurredAt)
                .note(note)
                .build();
    }
}
