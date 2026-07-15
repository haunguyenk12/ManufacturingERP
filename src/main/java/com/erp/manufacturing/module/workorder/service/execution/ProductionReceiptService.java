package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptPostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionReceiptService {

    private final ProductionReceiptRepository receiptRepository;
    private final ProductionReceiptLineRepository receiptLineRepository;
    private final InventoryMovementService movementService;
    private final WipTransactionService wipTransactionService;
    private final WorkOrderExecutionSupport support;
    private final ManufacturingExecutionMapper mapper;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.PRODUCTION_RECEIPT_POSTED, entityType = "ProductionReceipt", entityIdExpression = "receiptId.toString()")
    public ProductionReceiptResponse post(UUID workOrderId,
                                          ProductionReceiptPostRequest request,
                                          String idempotencyKey) {
        return postInternal(workOrderId, request, idempotencyKey);
    }

    @Transactional
    public ProductionReceiptResponse postInternal(UUID workOrderId,
                                           ProductionReceiptPostRequest request,
                                           String idempotencyKey) {
        String normalizedKey = support.normalizeIdempotencyKey(idempotencyKey);
        return receiptRepository.findWithLinesByIdempotencyKey(normalizedKey)
                .map(mapper::toResponse)
                .orElseGet(() -> postNew(workOrderId, request, normalizedKey));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_PRODUCTION_RECEIPT_MANAGE', #workOrderId)")
    public PageResult<ProductionReceiptResponse> list(UUID workOrderId, Pageable pageable) {
        support.findWorkOrder(workOrderId);
        Page<ProductionReceipt> page = receiptRepository.findByWorkOrderWorkOrderId(workOrderId, pageable);
        List<UUID> receiptIds = page.getContent().stream()
                .map(ProductionReceipt::getReceiptId)
                .toList();
        Map<UUID, List<ProductionReceiptLine>> linesByReceipt = receiptIds.isEmpty()
                ? Map.of()
                : receiptLineRepository.findByReceiptReceiptIdIn(receiptIds).stream()
                .collect(Collectors.groupingBy(line -> line.getReceipt().getReceiptId()));
        return PageResult.from(page.map(receipt -> mapper.toResponse(receipt, linesByReceipt)));
    }

    private ProductionReceiptResponse postNew(UUID workOrderId,
                                              ProductionReceiptPostRequest request,
                                              String normalizedKey) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureExecutable(workOrder);
        ProductionReceipt receipt = ProductionReceipt.builder()
                .workOrder(workOrder)
                .status(ProductionReceiptStatus.POSTED)
                .idempotencyKey(normalizedKey)
                .note(support.trimToNull(request.note()))
                .build();

        BigDecimal totalReceived = BigDecimal.ZERO;
        int index = 0;
        for (ProductionReceiptLineRequest lineRequest : request.lines()) {
            index++;
            Warehouse warehouse = support.findActiveWarehouseInPlant(lineRequest.warehouseId(), workOrder.getPlant());
            BigDecimal quantity = support.requirePositive(lineRequest.quantity(), "Receipt quantity");
            support.ensureDoesNotExceed(quantity, remainingCompletionQuantity(workOrder),
                    "Receipt quantity exceeds remaining planned quantity");

            InventoryMovementResult movementResult = movementService.receive(new InventoryReceiveCommand(
                    workOrder.getProductItem().getItemId(),
                    warehouse.getWarehouseId(),
                    lineRequest.lotId(),
                    lineRequest.lotCode(),
                    quantity,
                    support.trimToNull(lineRequest.reason()),
                    WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE,
                    workOrder.getWorkOrderId().toString()),
                    support.childIdempotencyKey(normalizedKey, index));

            StockMovement movement = movementResult.movement();
            if (movementResult.created()) {
                workOrder.setCompletedQuantity(workOrder.getCompletedQuantity().add(quantity));
                workOrder.markInProgress();
                if (workOrder.getCompletedQuantity().compareTo(workOrder.getPlannedQuantity()) >= 0) {
                    workOrder.complete(Instant.now());
                }
            }
            receipt.getLines().add(ProductionReceiptLine.builder()
                    .receipt(receipt)
                    .item(workOrder.getProductItem())
                    .warehouse(warehouse)
                    .lot(movement.getLot())
                    .quantity(quantity)
                    .stockMovement(movement)
                    .build());
            totalReceived = totalReceived.add(quantity);
        }

        ProductionReceipt saved = receiptRepository.save(receipt);
        wipTransactionService.recordOutputCompleted(workOrder, totalReceived, saved.getReceiptId());
        return mapper.toResponse(saved);
    }

    private BigDecimal remainingCompletionQuantity(WorkOrder workOrder) {
        return workOrder.getPlannedQuantity().subtract(workOrder.getCompletedQuantity());
    }
}
