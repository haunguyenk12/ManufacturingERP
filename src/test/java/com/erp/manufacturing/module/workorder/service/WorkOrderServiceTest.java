package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.mapper.WorkOrderMapper;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionReceiptService;
import com.erp.manufacturing.module.workorder.service.execution.WipTransactionService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderService tests")
class WorkOrderServiceTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemLookupService itemLookupService;
    @Mock BomLookupService bomLookupService;
    @Mock MaterialReservationService materialReservationService;
    @Mock MaterialIssueService materialIssueService;
    @Mock WipTransactionService wipTransactionService;
    @Mock ProductionReceiptService productionReceiptService;

    WorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(
                workOrderRepository,
                organizationLookupService,
                itemLookupService,
                bomLookupService,
                materialReservationService,
                materialIssueService,
                wipTransactionService,
                productionReceiptService,
                new WorkOrderMapper());
    }

    @Test
    void create_snapshotsActiveBomDirectRequirements() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        Plant plant = plant(plantId, companyId, OrganizationStatus.ACTIVE);
        Item product = item(productId, companyId, "FG-100", ItemType.FINISHED_GOOD, false);
        Warehouse warehouse = warehouse(warehouseId, plant, OrganizationStatus.ACTIVE);
        BomHeader bom = activeBom(product);
        bom.getLines().add(bomLine(bom, item(componentId, companyId, "RM-001", ItemType.RAW_MATERIAL, false),
                10, "2", "0.100000"));

        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant);
        when(workOrderRepository.existsByPlantPlantIdAndWorkOrderNo(plantId, "WO-001")).thenReturn(false);
        when(itemLookupService.getActiveItem(productId)).thenReturn(product);
        when(organizationLookupService.getActiveWarehouseInPlant(warehouseId, plantId)).thenReturn(warehouse);
        when(bomLookupService.getActiveBom(companyId, productId)).thenReturn(bom);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrderResponse response = service.create(plantId, new WorkOrderCreateRequest(
                " WO-001 ", productId, warehouseId, new BigDecimal("10"), null, null, " First run "));

        assertThat(response.workOrderNo()).isEqualTo("WO-001");
        assertThat(response.status()).isEqualTo(WorkOrderStatus.DRAFT.name());
        assertThat(response.componentLines()).hasSize(1);
        assertThat(response.componentLines().get(0).requiredQuantity()).isEqualByComparingTo("22");
        assertThat(response.notes()).isEqualTo("First run");
    }

    @Test
    void create_productWrongType_fails() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Plant plant = plant(plantId, companyId, OrganizationStatus.ACTIVE);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant);
        when(workOrderRepository.existsByPlantPlantIdAndWorkOrderNo(plantId, "WO-001")).thenReturn(false);
        when(itemLookupService.getActiveItem(productId))
                .thenReturn(item(productId, companyId, "RM-001", ItemType.RAW_MATERIAL, false));

        assertThatThrownBy(() -> service.create(plantId, new WorkOrderCreateRequest(
                "WO-001", productId, warehouseId, BigDecimal.ONE, null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(bomLookupService);
    }

    @Test
    void update_nonDraftWorkOrder_fails() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.update(workOrder.getWorkOrderId(),
                new WorkOrderUpdateRequest(null, new BigDecimal("12"), null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
    }

    @Test
    void release_draftWorkOrder_setsReleasedStatus() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(workOrder)).thenReturn(workOrder);

        service.release(workOrder.getWorkOrderId());

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.RELEASED);
        assertThat(workOrder.getReleasedAt()).isNotNull();
        verify(wipTransactionService).recordStart(workOrder);
    }

    @Test
    void issueComponent_delegatesToMaterialIssueDocumentService() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        UUID issueWarehouseId = workOrder.getOutputWarehouse().getWarehouseId();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        service.issueComponent(workOrder.getWorkOrderId(), new WorkOrderComponentIssueRequest(
                line.getComponentLineId(), issueWarehouseId, null, null, new BigDecimal("4"), "Issue to WO"),
                "KEY-ISSUE");

        verify(materialIssueService).postInternal(eq(workOrder.getWorkOrderId()), any(MaterialIssuePostRequest.class), eq("KEY-ISSUE"));
    }

    @Test
    void completeOutput_delegatesToProductionReceiptDocumentService() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        service.completeOutput(workOrder.getWorkOrderId(), new WorkOrderOutputCompletionRequest(
                null, null, new BigDecimal("10"), "Complete WO"), "KEY-COMPLETE");

        verify(productionReceiptService).postInternal(eq(workOrder.getWorkOrderId()), any(ProductionReceiptPostRequest.class), eq("KEY-COMPLETE"));
    }

    @Test
    void cancel_releasedWorkOrderWithIssuedQuantity_fails() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        workOrder.getComponentLines().get(0).setIssuedQuantity(BigDecimal.ONE);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.cancel(workOrder.getWorkOrderId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
    }

    private WorkOrder workOrder(WorkOrderStatus status, BigDecimal plannedQuantity) {
        UUID companyId = UUID.randomUUID();
        Plant plant = plant(UUID.randomUUID(), companyId, OrganizationStatus.ACTIVE);
        Item product = item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD, false);
        Warehouse outputWarehouse = warehouse(UUID.randomUUID(), plant, OrganizationStatus.ACTIVE);
        BomHeader bom = activeBom(product);
        Item component = item(UUID.randomUUID(), companyId, "RM-001", ItemType.RAW_MATERIAL, false);
        BomLine bomLine = bomLine(bom, component, 10, "1", "0");
        bom.getLines().add(bomLine);
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(UUID.randomUUID())
                .company(plant.getCompany())
                .plant(plant)
                .workOrderNo("WO-001")
                .productItem(product)
                .bom(bom)
                .bomRevision(bom.getRevision())
                .outputWarehouse(outputWarehouse)
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

    private BomHeader activeBom(Item product) {
        return BomHeader.builder()
                .bomId(UUID.randomUUID())
                .company(product.getCompany())
                .parentItem(product)
                .revision("R1")
                .status(BomStatus.ACTIVE)
                .lines(new ArrayList<>())
                .build();
    }

    private BomLine bomLine(BomHeader bom, Item component, int lineNo, String quantityPer, String scrapRate) {
        return BomLine.builder()
                .lineId(UUID.randomUUID())
                .bom(bom)
                .componentItem(component)
                .lineNo(lineNo)
                .quantityPer(new BigDecimal(quantityPer))
                .scrapRate(new BigDecimal(scrapRate))
                .build();
    }

    private Item item(UUID itemId, UUID companyId, String code, ItemType type, boolean lotTracked) {
        return Item.builder()
                .itemId(itemId)
                .company(company(companyId))
                .code(code)
                .name(code)
                .type(type)
                .unit("EA")
                .lotTracked(lotTracked)
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private Plant plant(UUID plantId, UUID companyId, OrganizationStatus status) {
        return Plant.builder()
                .plantId(plantId)
                .company(company(companyId))
                .code("P1")
                .name("Plant 1")
                .status(status)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, Plant plant, OrganizationStatus status) {
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant)
                .code("WH1")
                .name("Warehouse 1")
                .type(WarehouseType.RAW_MATERIAL)
                .status(status)
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
