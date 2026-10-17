package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationResult;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.SalesOrderCoverageLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderReservationServiceTest {

    @Mock SalesOrderRepository salesOrderRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock SalesOrderStockReservationService stockReservationService;
    @Mock SalesOrderCoverageLookupService coverageLookupService;

    SalesOrderReservationService service;

    private SalesOrder order;
    private SalesOrderLine line;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        SalesOrderMapper mapper = new SalesOrderMapper();
        service = new SalesOrderReservationService(salesOrderRepository, organizationLookupService,
                stockReservationService, new SalesOrderDetailAssembler(stockReservationService, coverageLookupService, mapper));
        Company company = Company.builder().companyId(UUID.randomUUID()).code("C").build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").build();
        warehouse = Warehouse.builder().warehouseId(UUID.randomUUID()).plant(plant).code("FG").build();
        order = SalesOrder.builder().salesOrderId(UUID.randomUUID()).company(company).plant(plant)
                .orderNo("SO-1").customerName("ACME").orderDate(LocalDate.of(2026, 10, 1))
                .status(SalesOrderStatus.CONFIRMED).build();
        order.setVersion(3L);
        line = SalesOrderLine.builder().salesOrderLineId(UUID.randomUUID()).salesOrder(order)
                .item(Item.builder().itemId(UUID.randomUUID()).code("FG").unit("EA").build())
                .lineNo(1).orderedQuantity(new BigDecimal("10")).fulfilledQuantity(new BigDecimal("2"))
                .dueDate(LocalDate.of(2026, 10, 20)).build();
        order.getLines().add(line);
        lenient().when(salesOrderRepository.findWithDetailsBySalesOrderId(order.getSalesOrderId()))
                .thenReturn(Optional.of(order));
    }

    @Test
    void reserve_draftOrder_isAStateConflict_andTouchesNoStock() {
        order.setStatus(SalesOrderStatus.DRAFT);

        assertCode(() -> service.reserve(order.getSalesOrderId(), request(3L, "1")), BusinessErrorCode.STATE_CONFLICT);

        verifyNoInteractions(stockReservationService);
    }

    @Test
    void reserve_staleVersion_failsBeforeAnythingElse() {
        assertCode(() -> service.reserve(order.getSalesOrderId(), request(2L, "1")),
                BusinessErrorCode.CONCURRENT_MODIFICATION);

        verifyNoInteractions(stockReservationService, organizationLookupService);
        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    /** 10 ordered − 2 QC-accepted − 3 held − 4 covered by work orders = 1 left; asking for 2 is refused. */
    @Test
    void reserve_moreThanTheUncoveredQuantity_isRefusedBeforeHoldingAnything() {
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), order.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(stockReservationService.sumActiveHeldBySalesOrderLineIds(List.of(line.getSalesOrderLineId())))
                .thenReturn(Map.of(line.getSalesOrderLineId(), new BigDecimal("3")));
        when(coverageLookupService.sumOpenCoverageBySalesOrderLineIds(List.of(line.getSalesOrderLineId())))
                .thenReturn(Map.of(line.getSalesOrderLineId(), new BigDecimal("4")));

        assertCode(() -> service.reserve(order.getSalesOrderId(), request(3L, "2")),
                BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);

        verify(stockReservationService, never()).reserve(any(), any(), any(), any(), any(), any());
        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void reserve_withinTheUncoveredQuantity_holdsThroughTheStockServiceAndBumpsTheOrderVersion() {
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), order.getPlant().getPlantId()))
                .thenReturn(warehouse);
        MaterialReservation hold = MaterialReservation.builder().reservationId(UUID.randomUUID())
                .salesOrderId(order.getSalesOrderId()).salesOrderLineId(line.getSalesOrderLineId())
                .item(line.getItem()).warehouse(warehouse).quantity(new BigDecimal("8"))
                .consumedQuantity(BigDecimal.ZERO).status(MaterialReservationStatus.ACTIVE).build();
        when(stockReservationService.reserve(order.getSalesOrderId(), line.getSalesOrderLineId(), line.getItem(),
                warehouse, null, new BigDecimal("8"))).thenReturn(List.of(hold));

        SalesOrderReservationResult result = service.reserve(order.getSalesOrderId(), request(3L, "8"));

        assertThat(result.reservations()).singleElement()
                .satisfies(r -> assertThat(r.remainingHeldQuantity()).isEqualByComparingTo("8"));
        verify(salesOrderRepository).saveAndFlush(order);
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    void reserve_lineOfAnotherOrder_isNotFound() {
        SalesOrderReservationCreateRequest request = new SalesOrderReservationCreateRequest(
                3L, UUID.randomUUID(), warehouse.getWarehouseId(), null, BigDecimal.ONE);

        assertCode(() -> service.reserve(order.getSalesOrderId(), request), ValidationErrorCode.RESOURCE_NOT_FOUND);

        verifyNoInteractions(stockReservationService);
    }

    private SalesOrderReservationCreateRequest request(long version, String quantity) {
        return new SalesOrderReservationCreateRequest(version, line.getSalesOrderLineId(),
                warehouse.getWarehouseId(), null, new BigDecimal(quantity));
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
