package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderMaterialReadinessLineResponse;
import com.erp.manufacturing.module.workorder.repository.ComponentQuantityProjection;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderReleaseGate tests")
class WorkOrderReleaseGateTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock MaterialReservationRepository reservationRepository;
    @Mock WorkOrderBlockRecorder blockRecorder;

    WorkOrderReleaseGate gate;

    @BeforeEach
    void setUp() {
        gate = new WorkOrderReleaseGate(workOrderRepository, reservationRepository, blockRecorder);
    }

    @Test
    void ensureMaterialReady_allComponentsFullyReserved_shouldPass() {
        WorkOrder workOrder = workOrder(new BigDecimal("10"), 2);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(reservationRepository.sumActiveRemainingByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(List.of(
                        projection(workOrder.getComponentLines().get(0).getComponentLineId(), "10"),
                        projection(workOrder.getComponentLines().get(1).getComponentLineId(), "10")));

        gate.ensureMaterialReady(workOrder.getWorkOrderId());

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.DRAFT);
        verifyNoInteractions(blockRecorder);
    }

    @Test
    void ensureMaterialReady_partialReservation_shouldBlockAndThrow() {
        WorkOrder workOrder = workOrder(new BigDecimal("10"), 2);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(reservationRepository.sumActiveRemainingByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(List.of(
                        projection(workOrder.getComponentLines().get(0).getComponentLineId(), "10"),
                        projection(workOrder.getComponentLines().get(1).getComponentLineId(), "4")));

        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> gate.ensureMaterialReady(workOrderId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        // D9 (debt #23): the gate no longer writes BLOCKED itself — it delegates to a bean that
        // returns normally so its REQUIRES_NEW transaction commits before this refusal is thrown.
        // Whether the row really survives the caller's rollback is only provable against a real
        // database: see ProductionFlowE2EIT.
        verify(blockRecorder).recordBlocked(workOrderId, "1/2 components short on reservation");
    }

    @Test
    void ensureMaterialReady_noReservation_shouldBlockAndThrow() {
        WorkOrder workOrder = workOrder(new BigDecimal("10"), 1);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(reservationRepository.sumActiveRemainingByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(List.of());

        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> gate.ensureMaterialReady(workOrderId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verify(blockRecorder).recordBlocked(workOrderId, "1/1 components short on reservation");
    }

    @Test
    void ensureMaterialReady_alreadyIssuedQuantityCountsTowardsCoverage() {
        WorkOrder workOrder = workOrder(new BigDecimal("10"), 1);
        // 6 already issued -> only 4 still need to be reserved.
        workOrder.getComponentLines().get(0).setIssuedQuantity(new BigDecimal("6"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(reservationRepository.sumActiveRemainingByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(List.of(projection(workOrder.getComponentLines().get(0).getComponentLineId(), "4")));

        gate.ensureMaterialReady(workOrder.getWorkOrderId());

        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void evaluate_usesSingleAggregateQuery_andReportsShortagePerComponent() {
        WorkOrder workOrder = workOrder(new BigDecimal("10"), 2);
        when(reservationRepository.sumActiveRemainingByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(List.of(projection(workOrder.getComponentLines().get(0).getComponentLineId(), "6")));

        List<WorkOrderMaterialReadinessLineResponse> lines = gate.evaluate(workOrder);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).reservedQuantity()).isEqualByComparingTo("6");
        assertThat(lines.get(0).shortageQuantity()).isEqualByComparingTo("4");
        assertThat(lines.get(1).reservedQuantity()).isEqualByComparingTo("0");
        assertThat(lines.get(1).shortageQuantity()).isEqualByComparingTo("10");
        // Exactly one aggregate query for the whole work order — no per-component loop.
        verify(reservationRepository, times(1)).sumActiveRemainingByWorkOrderId(workOrder.getWorkOrderId());
        verifyNoMoreInteractions(reservationRepository);
    }

    private ComponentQuantityProjection projection(UUID componentLineId, String quantity) {
        return new ComponentQuantityProjection() {
            @Override public UUID getComponentLineId() { return componentLineId; }
            @Override public BigDecimal getQuantity() { return new BigDecimal(quantity); }
        };
    }

    private WorkOrder workOrder(BigDecimal plannedQuantity, int componentCount) {
        UUID companyId = UUID.randomUUID();
        Plant plant = plant(UUID.randomUUID(), companyId);
        Item product = item(companyId, "FG-100", ItemType.FINISHED_GOOD);
        BomHeader bom = BomHeader.builder()
                .bomId(UUID.randomUUID())
                .company(product.getCompany())
                .parentItem(product)
                .revision("R1")
                .status(BomStatus.ACTIVE)
                .lines(new ArrayList<>())
                .build();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(UUID.randomUUID())
                .company(plant.getCompany())
                .plant(plant)
                .workOrderNo("WO-001")
                .productItem(product)
                .bom(bom)
                .bomRevision("R1")
                .outputWarehouse(warehouse(plant))
                .plannedQuantity(plannedQuantity)
                .completedQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.DRAFT)
                .componentLines(new ArrayList<>())
                .build();
        for (int i = 0; i < componentCount; i++) {
            Item component = item(companyId, "RM-00" + (i + 1), ItemType.RAW_MATERIAL);
            BomLine bomLine = BomLine.builder()
                    .lineId(UUID.randomUUID())
                    .bom(bom)
                    .componentItem(component)
                    .lineNo((i + 1) * 10)
                    .quantityPer(BigDecimal.ONE)
                    .scrapRate(BigDecimal.ZERO)
                    .build();
            workOrder.getComponentLines().add(WorkOrderComponentLine.builder()
                    .componentLineId(UUID.randomUUID())
                    .workOrder(workOrder)
                    .bomLine(bomLine)
                    .componentItem(component)
                    .lineNo((i + 1) * 10)
                    .quantityPer(BigDecimal.ONE)
                    .scrapRate(BigDecimal.ZERO)
                    .requiredQuantity(plannedQuantity)
                    .issuedQuantity(BigDecimal.ZERO)
                    .build());
        }
        return workOrder;
    }

    private Item item(UUID companyId, String code, ItemType type) {
        return Item.builder()
                .itemId(UUID.randomUUID())
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

    private Warehouse warehouse(Plant plant) {
        return Warehouse.builder()
                .warehouseId(UUID.randomUUID())
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
