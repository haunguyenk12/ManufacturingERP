package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialReservationCreateRequest;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaterialReservationService tests")
class MaterialReservationServiceTest {

    @Mock MaterialReservationRepository reservationRepository;
    @Mock StockBalanceRepository stockBalanceRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;

    MaterialReservationService service;

    @BeforeEach
    void setUp() {
        WorkOrderExecutionSupport support = new WorkOrderExecutionSupport(
                workOrderRepository, organizationLookupService, inventoryAvailabilityService);
        service = new MaterialReservationService(
                reservationRepository,
                stockBalanceRepository,
                support,
                new ManufacturingExecutionMapper());
    }

    @Test
    void reserve_success_increasesReservedQuantity() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        StockBalance balance = StockBalance.builder()
                .item(line.getComponentItem())
                .warehouse(warehouse)
                .quantity(new BigDecimal("10"))
                .reservedQuantity(BigDecimal.ZERO)
                .build();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(reservationRepository.sumActiveRemainingByComponentLineId(line.getComponentLineId()))
                .thenReturn(BigDecimal.ZERO);
        when(inventoryAvailabilityService.getStockBalanceForIssue(
                line.getComponentItem(), warehouse.getWarehouseId(), null))
                .thenReturn(balance);
        when(reservationRepository.save(any(MaterialReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reserve(workOrder.getWorkOrderId(), new MaterialReservationCreateRequest(
                line.getComponentLineId(), warehouse.getWarehouseId(), null, new BigDecimal("4")));

        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("4");
        verify(stockBalanceRepository).save(balance);
    }

    @Test
    void reserve_overAvailableStock_fails() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        StockBalance balance = StockBalance.builder()
                .item(line.getComponentItem())
                .warehouse(warehouse)
                .quantity(new BigDecimal("3"))
                .reservedQuantity(BigDecimal.ZERO)
                .build();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(reservationRepository.sumActiveRemainingByComponentLineId(line.getComponentLineId()))
                .thenReturn(BigDecimal.ZERO);
        when(inventoryAvailabilityService.getStockBalanceForIssue(
                line.getComponentItem(), warehouse.getWarehouseId(), null))
                .thenReturn(balance);

        assertThatThrownBy(() -> service.reserve(workOrder.getWorkOrderId(), new MaterialReservationCreateRequest(
                line.getComponentLineId(), warehouse.getWarehouseId(), null, new BigDecimal("4"))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void release_activeReservation_releasesReservedQuantity() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        MaterialReservation reservation = reservation(workOrder, line, warehouse, new BigDecimal("4"));
        StockBalance balance = StockBalance.builder()
                .item(line.getComponentItem())
                .warehouse(warehouse)
                .quantity(new BigDecimal("10"))
                .reservedQuantity(new BigDecimal("4"))
                .build();
        when(reservationRepository.findWithDetailsByReservationId(reservation.getReservationId()))
                .thenReturn(Optional.of(reservation));
        when(inventoryAvailabilityService.getStockBalanceForIssue(
                line.getComponentItem(), warehouse.getWarehouseId(), null))
                .thenReturn(balance);
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        service.release(workOrder.getWorkOrderId(), reservation.getReservationId());

        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("0");
        assertThat(reservation.getStatus()).isEqualTo(MaterialReservationStatus.RELEASED);
    }

    private MaterialReservation reservation(WorkOrder workOrder,
                                            WorkOrderComponentLine line,
                                            Warehouse warehouse,
                                            BigDecimal quantity) {
        return MaterialReservation.builder()
                .reservationId(UUID.randomUUID())
                .workOrder(workOrder)
                .componentLine(line)
                .item(line.getComponentItem())
                .warehouse(warehouse)
                .quantity(quantity)
                .consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE)
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
