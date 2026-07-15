package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class WorkOrderExecutionSupport {

    static final String WORK_ORDER_REFERENCE_TYPE = "WORK_ORDER";
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 120;

    private final WorkOrderRepository workOrderRepository;
    private final OrganizationLookupService organizationLookupService;
    private final InventoryAvailabilityService inventoryAvailabilityService;

    WorkOrder findWorkOrder(UUID workOrderId) {
        return workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId));
    }

    void ensureExecutable(WorkOrder workOrder) {
        if (!workOrder.canExecute()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Work order is not released for execution");
        }
    }

    void ensureNotClosedForWip(WorkOrder workOrder) {
        if (!workOrder.canExecute()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "WIP transaction can only be recorded for released or in-progress work orders");
        }
    }

    WorkOrderComponentLine findComponentLine(WorkOrder workOrder, UUID componentLineId) {
        return workOrder.getComponentLines().stream()
                .filter(line -> line.getComponentLineId().equals(componentLineId))
                .findFirst()
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order component line", componentLineId));
    }

    /**
     * Lấy warehouse active, validate thuộc đúng plant của work order.
     * Delegate sang OrganizationLookupService để không vi phạm module boundary.
     */
    Warehouse findActiveWarehouseInPlant(UUID warehouseId, Plant plant) {
        return organizationLookupService.getActiveWarehouseInPlant(warehouseId, plant.getPlantId());
    }

    /**
     * Lấy StockBalance theo item + warehouse + lot.
     * Delegate sang InventoryAvailabilityService để không vi phạm module boundary.
     */
    StockBalance findStockBalance(Item item, Warehouse warehouse, UUID lotId) {
        return inventoryAvailabilityService.getStockBalanceForIssue(item, warehouse.getWarehouseId(), lotId);
    }

    void ensureAvailableForReservation(StockBalance balance, BigDecimal quantity) {
        if (balance.getLot() != null && !balance.getLot().canIssue()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Lot cannot be reserved in status: " + balance.getLot().getStatus());
        }
        if (balance.availableQuantity().compareTo(quantity) < 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.INSUFFICIENT_STOCK);
        }
    }

    BigDecimal requirePositive(BigDecimal quantity, String fieldName) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " must be greater than zero");
        }
        return quantity;
    }

    void ensureDoesNotExceed(BigDecimal quantity, BigDecimal remaining, String message) {
        if (quantity.compareTo(remaining) > 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED, message);
        }
    }

    String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "Idempotency-Key header is required");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw ExceptionFactory.custom(ValidationErrorCode.FIELD_TOO_LONG,
                    "Idempotency-Key must be at most " + IDEMPOTENCY_KEY_MAX_LENGTH + " characters");
        }
        return normalized;
    }

    String childIdempotencyKey(String parentKey, int index) {
        String prefix = parentKey.length() > 112 ? parentKey.substring(0, 112) : parentKey;
        return prefix + ":L" + index;
    }

    String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
