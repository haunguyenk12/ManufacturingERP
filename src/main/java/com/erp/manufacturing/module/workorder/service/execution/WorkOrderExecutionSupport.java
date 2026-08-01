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
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Work order is not released for execution");
        }
    }

    /**
     * Looser than {@link #ensureExecutable} on purpose — see {@link WorkOrder#canReserve()}.
     * Material may be reserved before the work order is released; issuing it may not.
     */
    void ensureReservable(WorkOrder workOrder) {
        if (!workOrder.canReserve()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Work order is closed for reservation");
        }
    }

    /**
     * Looser than {@link #ensureExecutable} on purpose — see {@link WorkOrder#canReceipt()}.
     * Output the shop floor already made may still be warehoused after the work order completed;
     * issuing material into it may not.
     */
    void ensureReceiptable(WorkOrder workOrder) {
        if (!workOrder.canReceipt()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Work order is not open for production receipts");
        }
    }

    void ensureNotClosedForWip(WorkOrder workOrder) {
        if (!workOrder.canExecute()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
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
            throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE,
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
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, message);
        }
    }

    String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
