package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryIssueCommand;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssuePostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialIssueService {

    private final MaterialIssueRepository materialIssueRepository;
    private final MaterialIssueLineRepository materialIssueLineRepository;
    private final MaterialReservationService reservationService;
    private final InventoryMovementService movementService;
    private final WipTransactionService wipTransactionService;
    private final WorkOrderExecutionSupport support;
    private final ManufacturingExecutionMapper mapper;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_POSTED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse post(UUID workOrderId, MaterialIssuePostRequest request, String idempotencyKey) {
        return postInternal(workOrderId, request, idempotencyKey);
    }

    @Transactional
    public MaterialIssueResponse postInternal(UUID workOrderId, MaterialIssuePostRequest request, String idempotencyKey) {
        String normalizedKey = support.normalizeIdempotencyKey(idempotencyKey);
        return materialIssueRepository.findWithLinesByIdempotencyKey(normalizedKey)
                .map(mapper::toResponse)
                .orElseGet(() -> postNew(workOrderId, request, normalizedKey));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #workOrderId)")
    public PageResult<MaterialIssueResponse> list(UUID workOrderId, Pageable pageable) {
        support.findWorkOrder(workOrderId);
        Page<MaterialIssue> page = materialIssueRepository.findByWorkOrderWorkOrderId(workOrderId, pageable);
        List<UUID> issueIds = page.getContent().stream()
                .map(MaterialIssue::getIssueId)
                .toList();
        Map<UUID, List<MaterialIssueLine>> linesByIssue = issueIds.isEmpty()
                ? Map.of()
                : materialIssueLineRepository.findByIssueIssueIdIn(issueIds).stream()
                .collect(Collectors.groupingBy(line -> line.getIssue().getIssueId()));
        return PageResult.from(page.map(issue -> mapper.toResponse(issue, linesByIssue)));
    }

    private MaterialIssueResponse postNew(UUID workOrderId,
                                          MaterialIssuePostRequest request,
                                          String normalizedKey) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        support.ensureExecutable(workOrder);
        MaterialIssue issue = MaterialIssue.builder()
                .workOrder(workOrder)
                .status(MaterialIssueStatus.POSTED)
                .idempotencyKey(normalizedKey)
                .note(support.trimToNull(request.note()))
                .build();

        BigDecimal totalIssued = BigDecimal.ZERO;
        int index = 0;
        for (MaterialIssueLineRequest lineRequest : request.lines()) {
            index++;
            WorkOrderComponentLine componentLine = support.findComponentLine(workOrder, lineRequest.componentLineId());
            Warehouse warehouse = support.findActiveWarehouseInPlant(lineRequest.warehouseId(), workOrder.getPlant());
            BigDecimal quantity = support.requirePositive(lineRequest.quantity(), "Issue quantity");
            support.ensureDoesNotExceed(quantity, componentLine.remainingQuantity(),
                    "Issue quantity exceeds remaining component requirement");

            MaterialReservation reservation = null;
            InventoryMovementResult movementResult;
            if (lineRequest.reservationId() != null) {
                reservation = reservationService.findActiveReservationForIssue(workOrderId, lineRequest.reservationId());
                validateReservationForIssue(reservation, componentLine, warehouse, lineRequest, quantity);
                movementResult = movementService.issueReserved(issueCommand(
                        componentLine,
                        warehouse,
                        reservation.getLot() != null ? reservation.getLot().getLotId() : null,
                        null,
                        quantity,
                        lineRequest.reason(),
                        workOrder),
                        support.childIdempotencyKey(normalizedKey, index));
                if (movementResult.created()) {
                    reservation.consume(quantity);
                }
            } else {
                movementResult = movementService.issue(issueCommand(
                        componentLine,
                        warehouse,
                        lineRequest.lotId(),
                        lineRequest.lotCode(),
                        quantity,
                        lineRequest.reason(),
                        workOrder),
                        support.childIdempotencyKey(normalizedKey, index));
            }

            StockMovement movement = movementResult.movement();
            if (movementResult.created()) {
                componentLine.addIssuedQuantity(quantity);
                workOrder.markInProgress();
            }
            issue.getLines().add(MaterialIssueLine.builder()
                    .issue(issue)
                    .componentLine(componentLine)
                    .reservation(reservation)
                    .item(componentLine.getComponentItem())
                    .warehouse(warehouse)
                    .lot(movement.getLot())
                    .quantity(quantity)
                    .stockMovement(movement)
                    .build());
            totalIssued = totalIssued.add(quantity);
        }

        MaterialIssue saved = materialIssueRepository.save(issue);
        wipTransactionService.recordMaterialIssued(workOrder, totalIssued, saved.getIssueId());
        return mapper.toResponse(saved);
    }

    private InventoryIssueCommand issueCommand(WorkOrderComponentLine componentLine,
                                               Warehouse warehouse,
                                               UUID lotId,
                                               String lotCode,
                                               BigDecimal quantity,
                                               String reason,
                                               WorkOrder workOrder) {
        return new InventoryIssueCommand(
                componentLine.getComponentItem().getItemId(),
                warehouse.getWarehouseId(),
                lotId,
                lotCode,
                quantity,
                support.trimToNull(reason),
                WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE,
                workOrder.getWorkOrderId().toString());
    }

    private void validateReservationForIssue(MaterialReservation reservation,
                                             WorkOrderComponentLine componentLine,
                                             Warehouse warehouse,
                                             MaterialIssueLineRequest lineRequest,
                                             BigDecimal quantity) {
        if (!reservation.getComponentLine().getComponentLineId().equals(componentLine.getComponentLineId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Reservation does not belong to the requested component line");
        }
        if (!reservation.getWarehouse().getWarehouseId().equals(warehouse.getWarehouseId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Reservation warehouse does not match issue warehouse");
        }
        UUID reservationLotId = reservation.getLot() != null ? reservation.getLot().getLotId() : null;
        if (lineRequest.lotId() != null && !lineRequest.lotId().equals(reservationLotId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Reservation lot does not match issue lot");
        }
        support.ensureDoesNotExceed(quantity, reservation.remainingQuantity(),
                "Issue quantity exceeds remaining reservation quantity");
    }
}

