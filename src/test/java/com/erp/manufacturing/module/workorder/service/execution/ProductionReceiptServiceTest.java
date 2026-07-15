package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptPostRequest;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductionReceiptService tests")
class ProductionReceiptServiceTest {

    @Mock ProductionReceiptRepository receiptRepository;
    @Mock ProductionReceiptLineRepository receiptLineRepository;
    @Mock InventoryMovementService movementService;
    @Mock WipTransactionService wipTransactionService;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;

    ProductionReceiptService service;

    @BeforeEach
    void setUp() {
        WorkOrderExecutionSupport support = new WorkOrderExecutionSupport(
                workOrderRepository, organizationLookupService, inventoryAvailabilityService);
        service = new ProductionReceiptService(
                receiptRepository,
                receiptLineRepository,
                movementService,
                wipTransactionService,
                support,
                new ManufacturingExecutionMapper());
    }

    @Test
    void post_receiptCompletesWorkOrder() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        StockMovement movement = movement(workOrder.getProductItem(), warehouse);
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-RECEIPT")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(movementService.receive(any(InventoryReceiveCommand.class), eq("KEY-RECEIPT:L1")))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), new ProductionReceiptPostRequest("Receipt", List.of(
                new ProductionReceiptLineRequest(
                        warehouse.getWarehouseId(),
                        null,
                        null,
                        new BigDecimal("10"),
                        "Complete"))), "KEY-RECEIPT");

        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("10");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        verify(wipTransactionService).recordOutputCompleted(eq(workOrder), eq(new BigDecimal("10")), any());
    }

    @Test
    void post_overPlannedQuantity_failsBeforeMovement() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-OVER")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);

        assertThatThrownBy(() -> service.post(workOrder.getWorkOrderId(), new ProductionReceiptPostRequest("Receipt", List.of(
                new ProductionReceiptLineRequest(
                        warehouse.getWarehouseId(),
                        null,
                        null,
                        new BigDecimal("11"),
                        null))), "KEY-OVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(movementService, never()).receive(any(), anyString());
    }

    private StockMovement movement(Item item, Warehouse warehouse) {
        return StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("KEY")
                .createdAt(Instant.now())
                .build();
    }

    private WorkOrder workOrder(WorkOrderStatus status, BigDecimal plannedQuantity) {
        UUID companyId = UUID.randomUUID();
        Plant plant = plant(UUID.randomUUID(), companyId);
        Item product = item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        BomHeader bom = BomHeader.builder()
                .bomId(UUID.randomUUID())
                .company(product.getCompany())
                .parentItem(product)
                .revision("R1")
                .status(BomStatus.ACTIVE)
                .lines(new ArrayList<>())
                .build();
        return WorkOrder.builder()
                .workOrderId(UUID.randomUUID())
                .company(plant.getCompany())
                .plant(plant)
                .workOrderNo("WO-001")
                .productItem(product)
                .bom(bom)
                .bomRevision("R1")
                .outputWarehouse(warehouse)
                .plannedQuantity(plannedQuantity)
                .completedQuantity(BigDecimal.ZERO)
                .status(status)
                .componentLines(new ArrayList<>())
                .build();
    }

    private Item item(UUID itemId, UUID companyId, String code, ItemType type) {
        return Item.builder()
                .itemId(itemId)
                .company(company(companyId))
                .code(code)
                .name(code)
                .type(type)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private Plant plant(UUID plantId, UUID companyId) {
        return Plant.builder()
                .plantId(plantId)
                .company(company(companyId))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, Plant plant) {
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant)
                .code("WH1")
                .name("Warehouse 1")
                .type(WarehouseType.FINISHED_GOODS)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Company company(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
