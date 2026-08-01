package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.sales.service.SalesOrderAllocationTarget;
import com.erp.manufacturing.module.sales.service.SalesOrderFulfillmentService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderDemandAllocation;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderDemandAllocationResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderDemandAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderDemandAllocationService tests")
class WorkOrderDemandAllocationServiceTest {

    private static final LocalDate DUE = LocalDate.of(2026, 9, 1);

    @Mock WorkOrderDemandAllocationRepository allocationRepository;
    @Mock SalesOrderFulfillmentService salesOrderFulfillmentService;

    WorkOrderDemandAllocationService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderDemandAllocationService(allocationRepository, salesOrderFulfillmentService);
    }

    // ── allocate ───────────────────────────────────────────────────────────

    @Test
    void allocate_salesOrderLineDemand_earmarksTheWorkOrderAndStartsProduction() {
        WorkOrder workOrder = workOrder(new BigDecimal("30"));
        UUID lineId = UUID.randomUUID();
        stubTargets(target(lineId, 1, DUE, "40", "0"));

        service.allocate(workOrder, lineId);

        ArgumentCaptor<WorkOrderDemandAllocation> captor =
                ArgumentCaptor.forClass(WorkOrderDemandAllocation.class);
        verify(allocationRepository).save(captor.capture());
        assertThat(captor.getValue().getSalesOrderLineId()).isEqualTo(lineId);
        assertThat(captor.getValue().getAllocatedQuantity()).isEqualByComparingTo("30");
        assertThat(captor.getValue().getFulfilledQuantity()).isEqualByComparingTo("0");
        verify(salesOrderFulfillmentService).markInProduction(List.of(lineId));
    }

    /**
     * MRP lot-sizing can propose more than the customer is still waiting for; the surplus is stock,
     * not a claim on the order.
     */
    @Test
    void allocate_workOrderLargerThanTheOpenQuantity_isCappedAtWhatTheLineStillNeeds() {
        WorkOrder workOrder = workOrder(new BigDecimal("100"));
        UUID lineId = UUID.randomUUID();
        stubTargets(target(lineId, 1, DUE, "40", "15"));

        service.allocate(workOrder, lineId);

        ArgumentCaptor<WorkOrderDemandAllocation> captor =
                ArgumentCaptor.forClass(WorkOrderDemandAllocation.class);
        verify(allocationRepository).save(captor.capture());
        assertThat(captor.getValue().getAllocatedQuantity()).isEqualByComparingTo("25");
    }

    /** {@code MANUAL}/{@code FORECAST} demand: no customer behind it, and that is not an error. */
    @Test
    void allocate_demandWithoutASalesOrderLine_createsNothingAndDoesNotThrow() {
        service.allocate(workOrder(new BigDecimal("30")), null);

        verifyNoInteractions(allocationRepository, salesOrderFulfillmentService);
    }

    @Test
    void allocate_lineAlreadyFullyCovered_createsNoAllocation() {
        UUID lineId = UUID.randomUUID();
        stubTargets(target(lineId, 1, DUE, "40", "40"));

        service.allocate(workOrder(new BigDecimal("30")), lineId);

        verify(allocationRepository, never()).save(any());
        verify(salesOrderFulfillmentService, never()).markInProduction(any());
    }

    @Test
    void allocate_salesOrderLineThatNoLongerResolves_createsNothingAndDoesNotThrow() {
        UUID lineId = UUID.randomUUID();
        when(salesOrderFulfillmentService.findAllocationTargets(List.of(lineId))).thenReturn(Map.of());

        service.allocate(workOrder(new BigDecimal("30")), lineId);

        verify(allocationRepository, never()).save(any());
    }

    // ── fulfill ────────────────────────────────────────────────────────────

    /**
     * Spec §7.1: the released quantity fills the earliest-due allocation first, and only spills over
     * once that one is covered. The repository deliberately returns the rows in the wrong order to
     * prove the service sorts rather than trusting the database.
     */
    @Test
    void fulfill_spreadsAcrossAllocationsEarliestDueDateFirst() {
        WorkOrder workOrder = workOrder(new BigDecimal("30"));
        UUID lateLineId = UUID.randomUUID();
        UUID earlyLineId = UUID.randomUUID();
        WorkOrderDemandAllocation late = allocation(workOrder, lateLineId, "20", "0");
        WorkOrderDemandAllocation early = allocation(workOrder, earlyLineId, "10", "0");
        when(allocationRepository.findByWorkOrderWorkOrderIdIn(List.of(workOrder.getWorkOrderId())))
                .thenReturn(List.of(late, early));
        stubTargets(
                target(lateLineId, 1, DUE.plusDays(10), "20", "0"),
                target(earlyLineId, 1, DUE, "10", "0"));

        service.fulfill(workOrder, new BigDecimal("14"));

        assertThat(early.getFulfilledQuantity()).isEqualByComparingTo("10");
        assertThat(late.getFulfilledQuantity()).isEqualByComparingTo("4");
        Map<UUID, BigDecimal> expected = new LinkedHashMap<>();
        expected.put(earlyLineId, new BigDecimal("10"));
        expected.put(lateLineId, new BigDecimal("4"));
        verify(salesOrderFulfillmentService).applyFulfillment(expected);
    }

    @Test
    void fulfill_moreThanEveryAllocationClaims_dropsTheSurplusWithoutFailing() {
        WorkOrder workOrder = workOrder(new BigDecimal("30"));
        UUID lineId = UUID.randomUUID();
        WorkOrderDemandAllocation allocation = allocation(workOrder, lineId, "10", "0");
        when(allocationRepository.findByWorkOrderWorkOrderIdIn(List.of(workOrder.getWorkOrderId())))
                .thenReturn(List.of(allocation));
        stubTargets(target(lineId, 1, DUE, "10", "0"));

        service.fulfill(workOrder, new BigDecimal("25"));

        assertThat(allocation.getFulfilledQuantity()).isEqualByComparingTo("10");
        verify(salesOrderFulfillmentService).applyFulfillment(Map.of(lineId, new BigDecimal("10")));
    }

    /** Another work order may have covered the line already — the allocation must not over-fulfil. */
    @Test
    void fulfill_lineAlreadyCoveredElsewhere_appliesNothingToIt() {
        WorkOrder workOrder = workOrder(new BigDecimal("30"));
        UUID lineId = UUID.randomUUID();
        WorkOrderDemandAllocation allocation = allocation(workOrder, lineId, "10", "0");
        when(allocationRepository.findByWorkOrderWorkOrderIdIn(List.of(workOrder.getWorkOrderId())))
                .thenReturn(List.of(allocation));
        stubTargets(target(lineId, 1, DUE, "10", "10"));

        service.fulfill(workOrder, new BigDecimal("10"));

        assertThat(allocation.getFulfilledQuantity()).isEqualByComparingTo("0");
        verify(allocationRepository, never()).saveAll(any());
        verify(salesOrderFulfillmentService, never()).applyFulfillment(any());
    }

    @Test
    void fulfill_workOrderWithoutAnyAllocation_touchesNoSalesOrder() {
        WorkOrder workOrder = workOrder(new BigDecimal("30"));
        when(allocationRepository.findByWorkOrderWorkOrderIdIn(List.of(workOrder.getWorkOrderId())))
                .thenReturn(List.of());

        service.fulfill(workOrder, new BigDecimal("10"));

        verify(salesOrderFulfillmentService, never()).applyFulfillment(any());
        verify(allocationRepository, never()).saveAll(any());
    }

    // ── read ───────────────────────────────────────────────────────────────

    /** Rule C15: a page of work orders costs one allocation query, not one per work order. */
    @Test
    void findByWorkOrderIds_groupsAWholePageInOneRoundTrip() {
        WorkOrder first = workOrder(new BigDecimal("30"));
        WorkOrder second = workOrder(new BigDecimal("30"));
        UUID firstLineId = UUID.randomUUID();
        UUID secondLineId = UUID.randomUUID();
        List<UUID> workOrderIds = List.of(first.getWorkOrderId(), second.getWorkOrderId());
        when(allocationRepository.findByWorkOrderWorkOrderIdIn(workOrderIds)).thenReturn(List.of(
                allocation(first, firstLineId, "10", "4"),
                allocation(second, secondLineId, "20", "0")));
        stubTargets(
                target(firstLineId, 1, DUE, "10", "4"),
                target(secondLineId, 2, DUE.plusDays(1), "20", "0"));

        Map<UUID, List<WorkOrderDemandAllocationResponse>> byWorkOrder =
                service.findByWorkOrderIds(workOrderIds);

        assertThat(byWorkOrder).hasSize(2);
        WorkOrderDemandAllocationResponse firstResponse = byWorkOrder.get(first.getWorkOrderId()).get(0);
        assertThat(firstResponse.salesOrderLineId()).isEqualTo(firstLineId);
        assertThat(firstResponse.salesOrderCode()).isEqualTo("SO-1");
        assertThat(firstResponse.allocatedQuantity()).isEqualByComparingTo("10");
        assertThat(firstResponse.fulfilledQuantity()).isEqualByComparingTo("4");
        assertThat(firstResponse.uom()).isEqualTo("EA");
        assertThat(firstResponse.dueDate()).isEqualTo(DUE);
        verify(allocationRepository, times(1)).findByWorkOrderWorkOrderIdIn(anyCollection());
        verify(salesOrderFulfillmentService, times(1)).findAllocationTargets(anyCollection());
    }

    @Test
    void findByWorkOrderIds_noWorkOrders_skipsTheQueryEntirely() {
        assertThat(service.findByWorkOrderIds(List.of())).isEmpty();

        verifyNoInteractions(allocationRepository, salesOrderFulfillmentService);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void stubTargets(SalesOrderAllocationTarget... targets) {
        Map<UUID, SalesOrderAllocationTarget> byLine = new LinkedHashMap<>();
        for (SalesOrderAllocationTarget target : targets) {
            byLine.put(target.salesOrderLineId(), target);
        }
        when(salesOrderFulfillmentService.findAllocationTargets(anyCollection())).thenReturn(byLine);
    }

    private SalesOrderAllocationTarget target(UUID lineId,
                                              int lineNo,
                                              LocalDate dueDate,
                                              String ordered,
                                              String fulfilled) {
        return new SalesOrderAllocationTarget(lineId, UUID.randomUUID(), "SO-" + lineNo, lineNo,
                dueDate, "EA", new BigDecimal(ordered), new BigDecimal(fulfilled));
    }

    private WorkOrderDemandAllocation allocation(WorkOrder workOrder,
                                                 UUID lineId,
                                                 String allocated,
                                                 String fulfilled) {
        return WorkOrderDemandAllocation.builder()
                .allocationId(UUID.randomUUID())
                .workOrder(workOrder)
                .salesOrderLineId(lineId)
                .allocatedQuantity(new BigDecimal(allocated))
                .fulfilledQuantity(new BigDecimal(fulfilled))
                .build();
    }

    private WorkOrder workOrder(BigDecimal plannedQuantity) {
        return WorkOrder.builder()
                .workOrderId(UUID.randomUUID())
                .plannedQuantity(plannedQuantity)
                .build();
    }
}
