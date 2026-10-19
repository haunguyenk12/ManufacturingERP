package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueCandidateResponse;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** G8b: where a component can be issued from — own reservations (normal) or free plant stock (supplemental). */
@ExtendWith(MockitoExtension.class)
class MaterialIssueCandidateServiceTest {

    @Mock MaterialReservationRepository reservationRepository;
    @Mock StockBalanceRepository stockBalanceRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;

    MaterialIssueCandidateService service;
    WorkOrder workOrder;
    WorkOrderComponentLine component;
    Warehouse raw;

    @BeforeEach
    void setUp() {
        service = new MaterialIssueCandidateService(reservationRepository, stockBalanceRepository,
                new WorkOrderExecutionSupport(workOrderRepository, organizationLookupService, inventoryAvailabilityService));
        Company company = Company.builder().companyId(UUID.randomUUID()).code("CO").name("Co").status(OrganizationStatus.ACTIVE).build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").name("P").status(OrganizationStatus.ACTIVE).build();
        raw = Warehouse.builder().warehouseId(UUID.randomUUID()).plant(plant).code("RAW").name("Raw")
                .type(WarehouseType.RAW_MATERIAL).status(OrganizationStatus.ACTIVE).build();
        Item item = Item.builder().itemId(UUID.randomUUID()).company(company).code("RM").name("Steel").type(ItemType.RAW_MATERIAL)
                .unit("KG").status(ItemStatus.ACTIVE).build();
        workOrder = WorkOrder.builder().workOrderId(UUID.randomUUID()).company(company).plant(plant).workOrderNo("WO-1")
                .plannedQuantity(BigDecimal.TEN).status(WorkOrderStatus.RELEASED).componentLines(new ArrayList<>()).build();
        component = WorkOrderComponentLine.builder().componentLineId(UUID.randomUUID()).workOrder(workOrder)
                .bomLine(BomLine.builder().lineId(UUID.randomUUID()).build()).componentItem(item).lineNo(10)
                .quantityPer(new BigDecimal("2")).scrapRate(BigDecimal.ZERO)
                .requiredQuantity(new BigDecimal("20")).issuedQuantity(new BigDecimal("12")).build();
        workOrder.getComponentLines().add(component);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
    }

    /** Quick-fill = min(left on the reservation, required − gross issued) = min(15, 8) = 8; an expired lot is listed, not hidden. */
    @Test
    void normal_listsOwnReservationsWithQuickFill_andFlagsAnExpiredLot() {
        InventoryLot expired = InventoryLot.builder().lotId(UUID.randomUUID()).item(component.getComponentItem()).lotCode("L-OLD")
                .status(LotStatus.AVAILABLE).expiresAt(Instant.now().minusSeconds(60)).build();
        MaterialReservation good = reservation(null, "15");
        MaterialReservation stale = reservation(expired, "3");
        when(reservationRepository.findByWorkOrderWorkOrderIdAndStatus(workOrder.getWorkOrderId(), MaterialReservationStatus.ACTIVE))
                .thenReturn(List.of(good, stale));

        var page = service.candidates(workOrder.getWorkOrderId(), null, MaterialIssueCandidateService.Mode.NORMAL,
                PageRequest.of(0, 20));

        MaterialIssueCandidateResponse first = page.content().get(0);
        assertThat(first.sourceType()).isEqualTo("RESERVATION");
        assertThat(first.suggestedQuantity()).isEqualByComparingTo("8");
        assertThat(first.eligible()).isTrue();
        MaterialIssueCandidateResponse second = page.content().get(1);
        assertThat(second.eligible()).isFalse();
        assertThat(second.ineligibleReason()).isEqualTo("LOT_EXPIRED");
        assertThat(second.suggestedQuantity()).isEqualByComparingTo("0");
        assertThat(page.totalElements()).isEqualTo(2);
    }

    /** FE Q8: supplemental sources are free, eligible stock in every warehouse of the plant. */
    @Test
    void supplemental_listsFreeStockAcrossThePlant_withoutASuggestion() {
        UUID otherWarehouse = UUID.randomUUID();
        when(organizationLookupService.resolveScope(ScopeResourceType.PLANT, workOrder.getPlant().getPlantId()))
                .thenReturn(new OrganizationScopeResolution(ScopeResourceType.PLANT, workOrder.getPlant().getPlantId(),
                        UUID.randomUUID(), List.of(raw.getWarehouseId(), otherWarehouse)));
        StockBalance free = StockBalance.builder().item(component.getComponentItem()).warehouse(raw)
                .quantity(new BigDecimal("40")).reservedQuantity(new BigDecimal("15")).build();
        when(stockBalanceRepository.findReservableBalancesFefo(eq(component.getComponentItem().getItemId()),
                eq(List.of(raw.getWarehouseId(), otherWarehouse)), eq(LotStatus.AVAILABLE), any(), any()))
                .thenReturn(new PageImpl<>(List.of(free)));

        var page = service.candidates(workOrder.getWorkOrderId(), component.getComponentLineId(),
                MaterialIssueCandidateService.Mode.SUPPLEMENTAL, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).sourceType()).isEqualTo("FREE_STOCK");
        assertThat(page.content().get(0).availableQuantity()).isEqualByComparingTo("25");
        assertThat(page.content().get(0).suggestedQuantity()).isNull();
        verify(stockBalanceRepository).findReservableBalancesFefo(any(), eq(List.of(raw.getWarehouseId(), otherWarehouse)),
                any(), any(), any());
    }

    private MaterialReservation reservation(InventoryLot lot, String quantity) {
        return MaterialReservation.builder().reservationId(UUID.randomUUID()).workOrder(workOrder).componentLine(component)
                .item(component.getComponentItem()).warehouse(raw).lot(lot).quantity(new BigDecimal(quantity))
                .consumedQuantity(BigDecimal.ZERO).status(MaterialReservationStatus.ACTIVE).build();
    }
}
