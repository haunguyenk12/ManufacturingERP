package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderMaterialReadinessLineResponse;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderMaterialReadinessResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderReleaseGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderReadinessService tests")
class WorkOrderReadinessServiceTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock WorkOrderReleaseGate releaseGate;

    WorkOrderReadinessService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderReadinessService(workOrderRepository, releaseGate);
    }

    @Test
    void materialReadiness_returnsShortagePerComponent() {
        WorkOrder workOrder = workOrder();
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(releaseGate.evaluate(workOrder)).thenReturn(List.of(new WorkOrderMaterialReadinessLineResponse(
                line.getComponentLineId(),
                line.getComponentItem().getItemId(),
                "RM-001",
                "RM-001",
                new BigDecimal("10"),
                new BigDecimal("2"),
                new BigDecimal("3"),
                new BigDecimal("5"))));

        WorkOrderMaterialReadinessResponse response = service.getMaterialReadiness(workOrder.getWorkOrderId());

        assertThat(response.ready()).isFalse();
        assertThat(response.workOrderNo()).isEqualTo("WO-001");
        assertThat(response.status()).isEqualTo(WorkOrderStatus.DRAFT.name());
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).shortageQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void materialReadiness_noShortage_shouldBeReady() {
        WorkOrder workOrder = workOrder();
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(releaseGate.evaluate(workOrder)).thenReturn(List.of(new WorkOrderMaterialReadinessLineResponse(
                line.getComponentLineId(),
                line.getComponentItem().getItemId(),
                "RM-001",
                "RM-001",
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("10"),
                BigDecimal.ZERO)));

        assertThat(service.getMaterialReadiness(workOrder.getWorkOrderId()).ready()).isTrue();
    }

    @Test
    void materialReadiness_unknownWorkOrder_shouldThrowNotFound() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMaterialReadiness(workOrderId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    private WorkOrder workOrder() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Plant plant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Item product = item(company, "FG-100", ItemType.FINISHED_GOOD);
        Item component = item(company, "RM-001", ItemType.RAW_MATERIAL);
        BomHeader bom = BomHeader.builder()
                .bomId(UUID.randomUUID())
                .company(company)
                .parentItem(product)
                .revision("R1")
                .status(BomStatus.ACTIVE)
                .lines(new ArrayList<>())
                .build();
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
                .company(company)
                .plant(plant)
                .workOrderNo("WO-001")
                .productItem(product)
                .bom(bom)
                .bomRevision("R1")
                .outputWarehouse(Warehouse.builder()
                        .warehouseId(UUID.randomUUID())
                        .plant(plant)
                        .code("WH1")
                        .name("Warehouse 1")
                        .type(WarehouseType.FINISHED_GOODS)
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .plannedQuantity(new BigDecimal("10"))
                .completedQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.DRAFT)
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
                .requiredQuantity(new BigDecimal("10"))
                .issuedQuantity(BigDecimal.ZERO)
                .build());
        return workOrder;
    }

    private Item item(Company company, String code, ItemType type) {
        return Item.builder()
                .itemId(UUID.randomUUID())
                .company(company)
                .code(code)
                .name(code)
                .type(type)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }
}
