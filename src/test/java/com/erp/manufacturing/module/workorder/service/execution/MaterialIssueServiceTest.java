package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.InventoryIssueCommand;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssuePostRequest;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
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
@DisplayName("MaterialIssueService tests")
class MaterialIssueServiceTest {

    @Mock MaterialIssueRepository issueRepository;
    @Mock MaterialIssueLineRepository issueLineRepository;
    @Mock MaterialReservationService reservationService;
    @Mock InventoryMovementService movementService;
    @Mock WipTransactionService wipTransactionService;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;

    MaterialIssueService service;

    @BeforeEach
    void setUp() {
        WorkOrderExecutionSupport support = new WorkOrderExecutionSupport(
                workOrderRepository, organizationLookupService, inventoryAvailabilityService);
        service = new MaterialIssueService(
                issueRepository,
                issueLineRepository,
                reservationService,
                movementService,
                wipTransactionService,
                support,
                new ManufacturingExecutionMapper());
    }

    @Test
    void post_fromReservation_consumesReservationAndIncrementsIssuedQuantity() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        MaterialReservation reservation = MaterialReservation.builder()
                .reservationId(UUID.randomUUID())
                .workOrder(workOrder)
                .componentLine(line)
                .item(line.getComponentItem())
                .warehouse(warehouse)
                .quantity(new BigDecimal("5"))
                .consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE)
                .build();
        StockMovement movement = movement(line.getComponentItem(), warehouse);

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-ISSUE")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(reservationService.findActiveReservationForIssue(workOrder.getWorkOrderId(), reservation.getReservationId()))
                .thenReturn(reservation);
        when(movementService.issueReserved(any(InventoryIssueCommand.class), eq("KEY-ISSUE:L1")))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(issueRepository.save(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(
                new MaterialIssueLineRequest(
                        line.getComponentLineId(),
                        reservation.getReservationId(),
                        warehouse.getWarehouseId(),
                        null,
                        null,
                        new BigDecimal("4"),
                        "Issue reserved"))), "KEY-ISSUE");

        assertThat(reservation.getConsumedQuantity()).isEqualByComparingTo("4");
        assertThat(reservation.getStatus()).isEqualTo(MaterialReservationStatus.ACTIVE);
        assertThat(line.getIssuedQuantity()).isEqualByComparingTo("4");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(wipTransactionService).recordMaterialIssued(eq(workOrder), eq(new BigDecimal("4")), any());
    }

    @Test
    void post_overIssue_failsBeforeStockMovement() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-OVER")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);

        assertThatThrownBy(() -> service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(
                new MaterialIssueLineRequest(
                        line.getComponentLineId(),
                        null,
                        warehouse.getWarehouseId(),
                        null,
                        null,
                        new BigDecimal("99"),
                        null))), "KEY-OVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(movementService, never()).issue(any(), anyString());
        verify(issueRepository, never()).save(any());
    }

    private StockMovement movement(Item item, Warehouse warehouse) {
        return StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.ISSUE)
                .direction(MovementDirection.OUT)
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
        Item component = item(UUID.randomUUID(), companyId, "RM-001", ItemType.RAW_MATERIAL);
        BomLine bomLine = BomLine.builder()
                .lineId(UUID.randomUUID())
                .bom(bom)
                .componentItem(component)
                .lineNo(10)
                .quantityPer(BigDecimal.ONE)
                .scrapRate(BigDecimal.ZERO)
                .build();
        WorkOrder workOrder = WorkOrder.builder()
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
        workOrder.getComponentLines().add(WorkOrderComponentLine.builder()
                .componentLineId(UUID.randomUUID())
                .workOrder(workOrder)
                .bomLine(bomLine)
                .componentItem(component)
                .lineNo(10)
                .quantityPer(BigDecimal.ONE)
                .scrapRate(BigDecimal.ZERO)
                .requiredQuantity(plannedQuantity)
                .issuedQuantity(BigDecimal.ZERO)
                .build());
        return workOrder;
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
                .type(WarehouseType.RAW_MATERIAL)
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
