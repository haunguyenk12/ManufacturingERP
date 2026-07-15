package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.*;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import com.erp.manufacturing.module.workorder.mapper.WorkOrderMapper;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionReceiptService;
import com.erp.manufacturing.module.workorder.service.execution.WipTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final BomLookupService bomLookupService;
    private final MaterialReservationService materialReservationService;
    private final MaterialIssueService materialIssueService;
    private final WipTransactionService wipTransactionService;
    private final ProductionReceiptService productionReceiptService;
    private final WorkOrderMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.WORK_ORDER_CREATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse create(UUID plantId, WorkOrderCreateRequest request) {
        Plant plant = organizationLookupService.getActivePlant(plantId);
        String workOrderNo = normalizeCode(request.workOrderNo(), "Work order number");
        if (workOrderRepository.existsByPlantPlantIdAndWorkOrderNo(plantId, workOrderNo)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Work order number", workOrderNo);
        }

        Item product = itemLookupService.getActiveItem(request.productItemId());
        ensureManufacturableProduct(product);
        ensureProductBelongsToPlantCompany(product, plant);
        Warehouse outputWarehouse = organizationLookupService.getActiveWarehouseInPlant(request.outputWarehouseId(), plantId);
        BigDecimal plannedQuantity = requirePositive(request.plannedQuantity(), "Planned quantity");
        ensureDateRange(request.plannedStartAt(), request.plannedEndAt());

        BomHeader bom = bomLookupService.getActiveBom(plant.getCompany().getCompanyId(), product.getItemId());
        WorkOrder workOrder = WorkOrder.builder()
                .company(plant.getCompany())
                .plant(plant)
                .workOrderNo(workOrderNo)
                .productItem(product)
                .bom(bom)
                .bomRevision(bom.getRevision())
                .outputWarehouse(outputWarehouse)
                .plannedQuantity(plannedQuantity)
                .completedQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.DRAFT)
                .plannedStartAt(request.plannedStartAt())
                .plannedEndAt(request.plannedEndAt())
                .notes(trimToNull(request.notes()))
                .build();
        snapshotComponentLines(workOrder, bom, plannedQuantity);
        return mapper.toResponse(workOrderRepository.save(workOrder));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SUPPLY_SUGGESTION_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.WORK_ORDER_CREATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse createFromMrp(UUID plantId, WorkOrderCreateRequest request) {
        return create(plantId, request);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_READ', 'PLANT', #plantId)")
    public PageResult<WorkOrderResponse> list(UUID plantId,
                                              WorkOrderStatus status,
                                              UUID productItemId,
                                              Pageable pageable) {
        organizationLookupService.getActivePlant(plantId);
        return PageResult.from(workOrderRepository.search(plantId, status, productItemId, pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_READ', #workOrderId)")
    public WorkOrderResponse get(UUID workOrderId) {
        return mapper.toResponse(findWorkOrder(workOrderId));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_UPDATED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse update(UUID workOrderId, WorkOrderUpdateRequest request) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        ensureDraft(workOrder);

        if (request.outputWarehouseId() != null) {
            workOrder.setOutputWarehouse(organizationLookupService.getActiveWarehouseInPlant(
                    request.outputWarehouseId(), workOrder.getPlant().getPlantId()));
        }
        if (request.plannedQuantity() != null) {
            BigDecimal plannedQuantity = requirePositive(request.plannedQuantity(), "Planned quantity");
            workOrder.setPlannedQuantity(plannedQuantity);
            recalculateRequirements(workOrder, plannedQuantity);
        }
        if (request.plannedStartAt() != null) {
            workOrder.setPlannedStartAt(request.plannedStartAt());
        }
        if (request.plannedEndAt() != null) {
            workOrder.setPlannedEndAt(request.plannedEndAt());
        }
        ensureDateRange(workOrder.getPlannedStartAt(), workOrder.getPlannedEndAt());
        if (request.notes() != null) {
            workOrder.setNotes(trimToNull(request.notes()));
        }
        return mapper.toResponse(workOrderRepository.save(workOrder));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_RELEASED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse release(UUID workOrderId) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        ensureDraft(workOrder);
        if (workOrder.getComponentLines().isEmpty()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot release work order without component requirements");
        }
        workOrder.release(Instant.now());
        WorkOrder saved = workOrderRepository.save(workOrder);
        wipTransactionService.recordStart(saved);
        return mapper.toResponse(saved);
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_CANCELLED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse cancel(UUID workOrderId) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        if (workOrder.getStatus() != WorkOrderStatus.DRAFT && workOrder.getStatus() != WorkOrderStatus.RELEASED) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only draft or released work orders can be cancelled");
        }
        if (hasAnyMovement(workOrder)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Work order with issued or completed quantity cannot be cancelled");
        }
        materialReservationService.cancelActiveReservations(workOrder);
        workOrder.cancel(Instant.now());
        return mapper.toResponse(workOrderRepository.save(workOrder));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_EXECUTE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_COMPONENT_ISSUED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse issueComponent(UUID workOrderId,
                                            WorkOrderComponentIssueRequest request,
                                            String idempotencyKey) {
        materialIssueService.postInternal(workOrderId, new MaterialIssuePostRequest(null, List.of(
                new MaterialIssueLineRequest(
                        request.componentLineId(),
                        null,
                        request.warehouseId(),
                        request.lotId(),
                        request.lotCode(),
                        request.quantity(),
                        request.reason()))), idempotencyKey);
        return mapper.toResponse(findWorkOrder(workOrderId));
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_EXECUTE', #workOrderId)")
    @Auditable(action = AuditAction.WORK_ORDER_COMPLETED, entityType = "WorkOrder", entityIdExpression = "workOrderId.toString()")
    public WorkOrderResponse completeOutput(UUID workOrderId,
                                            WorkOrderOutputCompletionRequest request,
                                            String idempotencyKey) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        productionReceiptService.postInternal(workOrderId, new ProductionReceiptPostRequest(null, List.of(
                new ProductionReceiptLineRequest(
                        workOrder.getOutputWarehouse().getWarehouseId(),
                        request.lotId(),
                        request.lotCode(),
                        request.quantity(),
                        request.reason()))), idempotencyKey);
        return mapper.toResponse(findWorkOrder(workOrderId));
    }

    private WorkOrder findWorkOrder(UUID workOrderId) {
        return workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId));
    }

    private void ensureManufacturableProduct(Item product) {
        if (product.getType() != ItemType.WIP && product.getType() != ItemType.FINISHED_GOOD) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Work order product must be WIP or FINISHED_GOOD");
        }
    }

    private void ensureProductBelongsToPlantCompany(Item product, Plant plant) {
        if (!product.getCompany().getCompanyId().equals(plant.getCompany().getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Product item must belong to the plant company");
        }
    }

    private void ensureDraft(WorkOrder workOrder) {
        if (!workOrder.isDraft()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only draft work orders can be changed");
        }
    }

    private void ensureExecutable(WorkOrder workOrder) {
        if (!workOrder.canExecute()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Work order is not released for execution");
        }
    }

    private void snapshotComponentLines(WorkOrder workOrder, BomHeader bom, BigDecimal plannedQuantity) {
        for (BomLine bomLine : bom.getLines()) {
            WorkOrderComponentLine line = WorkOrderComponentLine.builder()
                    .workOrder(workOrder)
                    .bomLine(bomLine)
                    .componentItem(bomLine.getComponentItem())
                    .lineNo(bomLine.getLineNo())
                    .quantityPer(bomLine.getQuantityPer())
                    .scrapRate(bomLine.getScrapRate())
                    .requiredQuantity(requiredQuantity(plannedQuantity, bomLine))
                    .issuedQuantity(BigDecimal.ZERO)
                    .build();
            workOrder.getComponentLines().add(line);
        }
    }

    private void recalculateRequirements(WorkOrder workOrder, BigDecimal plannedQuantity) {
        for (WorkOrderComponentLine line : workOrder.getComponentLines()) {
            line.setRequiredQuantity(plannedQuantity
                    .multiply(line.getQuantityPer())
                    .multiply(BigDecimal.ONE.add(line.getScrapRate())));
        }
    }

    private BigDecimal requiredQuantity(BigDecimal plannedQuantity, BomLine bomLine) {
        return plannedQuantity
                .multiply(bomLine.getQuantityPer())
                .multiply(BigDecimal.ONE.add(bomLine.getScrapRate()));
    }

    private WorkOrderComponentLine findComponentLine(WorkOrder workOrder, UUID componentLineId) {
        return workOrder.getComponentLines().stream()
                .filter(line -> line.getComponentLineId().equals(componentLineId))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order component line", componentLineId));
    }

    private boolean hasAnyMovement(WorkOrder workOrder) {
        return workOrder.getCompletedQuantity().compareTo(BigDecimal.ZERO) > 0
                || workOrder.getComponentLines().stream()
                .anyMatch(line -> line.getIssuedQuantity().compareTo(BigDecimal.ZERO) > 0);
    }

    private BigDecimal remainingCompletionQuantity(WorkOrder workOrder) {
        return workOrder.getPlannedQuantity().subtract(workOrder.getCompletedQuantity());
    }

    private void ensureDoesNotExceed(BigDecimal quantity, BigDecimal remaining, String message) {
        if (quantity.compareTo(remaining) > 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED, message);
        }
    }

    private BigDecimal requirePositive(BigDecimal quantity, String fieldName) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " must be greater than zero");
        }
        return quantity;
    }

    private void ensureDateRange(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Planned end date cannot be before planned start date");
        }
    }

    private String normalizeCode(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
