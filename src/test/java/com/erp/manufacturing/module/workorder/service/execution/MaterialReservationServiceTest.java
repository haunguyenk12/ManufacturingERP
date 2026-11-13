package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.module.inventory.service.StockBalanceReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.repository.WorkOrderIdempotencyRecordRepository;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    @Mock WorkOrderIdempotencyRecordRepository idempotencyRecordRepository;

    MaterialReservationService service;

    @BeforeEach
    void setUp() {
        WorkOrderExecutionSupport support = new WorkOrderExecutionSupport(
                workOrderRepository, organizationLookupService, inventoryAvailabilityService);
        service = new MaterialReservationService(
                reservationRepository,
                new StockBalanceReservationService(stockBalanceRepository),
                organizationLookupService,
                support,
                new ManufacturingExecutionMapper(),
                new WorkOrderIdempotency(idempotencyRecordRepository,
                        new IdempotencySupport(new ObjectMapper().findAndRegisterModules())));
    }

    /**
     * Rewritten in D9 (debt #22). This case used to assert that reserving on a {@code BLOCKED} work
     * order throws — the rule that deadlocked the system: reserving demanded {@code RELEASED} while
     * releasing demanded a full reservation, so a work order created from MRP could never be released
     * at all. {@code BLOCKED} is now exactly the state a planner reserves <em>out of</em>.
     */
    @ParameterizedTest
    @EnumSource(value = WorkOrderStatus.class, names = {"DRAFT", "PLANNED", "BLOCKED"})
    void reserve_isAllowedBeforeRelease(WorkOrderStatus status) {
        WorkOrder workOrder = workOrder(status, new BigDecimal("10"));
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
        when(organizationLookupService.getActiveWarehouseInPlant(
                warehouse.getWarehouseId(), workOrder.getPlant().getPlantId())).thenReturn(warehouse);
        when(reservationRepository.sumActiveRemainingByComponentLineId(line.getComponentLineId()))
                .thenReturn(BigDecimal.ZERO);
        when(inventoryAvailabilityService.getStockBalanceForIssue(
                line.getComponentItem(), warehouse.getWarehouseId(), null)).thenReturn(balance);
        when(reservationRepository.save(any(MaterialReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.reserve(workOrder.getWorkOrderId(), new MaterialReservationCreateRequest(
                line.getComponentLineId(), warehouse.getWarehouseId(), null, new BigDecimal("4"), 0L), null);

        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("4");
        verify(reservationRepository).save(any(MaterialReservation.class));
    }

    @ParameterizedTest
    @EnumSource(value = WorkOrderStatus.class, names = {"COMPLETED", "CANCELLED", "CLOSED"})
    void reserve_onClosedWorkOrder_shouldThrowBeforeTouchingStock(WorkOrderStatus status) {
        WorkOrder workOrder = workOrder(status, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        UUID workOrderId = workOrder.getWorkOrderId();
        MaterialReservationCreateRequest request = new MaterialReservationCreateRequest(
                line.getComponentLineId(), warehouse.getWarehouseId(), null, BigDecimal.ONE, 0L);

        assertThatThrownBy(() -> service.reserve(workOrderId, request, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(stockBalanceRepository);
        verify(reservationRepository, never()).save(any());
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
                line.getComponentLineId(), warehouse.getWarehouseId(), null, new BigDecimal("4"), 0L), null);

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
                line.getComponentLineId(), warehouse.getWarehouseId(), null, new BigDecimal("4"), 0L), null))
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

        service.release(workOrder.getWorkOrderId(), reservation.getReservationId(), 0L, null);

        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("0");
        assertThat(reservation.getStatus()).isEqualTo(MaterialReservationStatus.RELEASED);
    }

    // ── G8a ────────────────────────────────────────────────────────────────────────────────────────

    /** D2: a line that names a warehouse is reserved from it only — another one is refused, not used. */
    @Test
    void reserve_lineWithAWarehouse_fromAnotherWarehouse_isRefusedOnTheWarehouseField() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.PLANNED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse raw = warehouse(UUID.randomUUID(), workOrder.getPlant());
        line.setWarehouse(raw);
        Warehouse other = workOrder.getOutputWarehouse();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(other.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(other);

        assertThatThrownBy(() -> service.reserve(workOrder.getWorkOrderId(), new MaterialReservationCreateRequest(
                line.getComponentLineId(), other.getWarehouseId(), null, BigDecimal.ONE, 0L), null))
                .isInstanceOf(com.erp.manufacturing.common.exception.MultiErrorException.class)
                .satisfies(ex -> {
                    var error = (com.erp.manufacturing.common.exception.MultiErrorException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH);
                    assertThat(error.getFieldErrors()).containsOnlyKeys("warehouseId");
                });
        verifyNoInteractions(stockBalanceRepository);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve_staleWorkOrderVersion_throwsConcurrentModificationBeforeTouchingStock() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.PLANNED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.reserve(workOrder.getWorkOrderId(), new MaterialReservationCreateRequest(
                line.getComponentLineId(), UUID.randomUUID(), null, BigDecimal.ONE, 7L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        verifyNoInteractions(stockBalanceRepository, organizationLookupService);
    }

    /**
     * FE F2: 15 in the line's warehouse, 500 elsewhere in the plant — only the line's warehouse is asked,
     * so 15 is reserved and 5 stays short. The plant scope is still resolved (for lines without a
     * warehouse) but never queried for this line.
     */
    @Test
    void reserveAutomatically_lineWithAWarehouse_looksInThatWarehouseOnly() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.PLANNED, new BigDecimal("20"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse raw = warehouse(UUID.randomUUID(), workOrder.getPlant());
        line.setWarehouse(raw);
        StockBalance fifteen = StockBalance.builder().item(line.getComponentItem()).warehouse(raw)
                .quantity(new BigDecimal("15")).reservedQuantity(BigDecimal.ZERO).build();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.resolveScope(ScopeResourceType.PLANT, workOrder.getPlant().getPlantId()))
                .thenReturn(new com.erp.manufacturing.module.organization.service.OrganizationScopeResolution(
                        ScopeResourceType.PLANT, workOrder.getPlant().getPlantId(), UUID.randomUUID(),
                        java.util.List.of(raw.getWarehouseId(), UUID.randomUUID())));
        when(reservationRepository.sumActiveRemainingByComponentLineId(line.getComponentLineId())).thenReturn(BigDecimal.ZERO);
        when(stockBalanceRepository.findIssuableBalancesFefo(eq(line.getComponentItem().getItemId()),
                eq(java.util.List.of(raw.getWarehouseId())), eq(LotStatus.AVAILABLE), any(java.time.Instant.class)))
                .thenReturn(java.util.List.of(fifteen));
        when(reservationRepository.save(any(MaterialReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        var created = service.reserveAutomatically(workOrder.getWorkOrderId(),
                new com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest(0L), null);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).quantity()).isEqualByComparingTo("15");
        assertThat(fifteen.getReservedQuantity()).isEqualByComparingTo("15");
        verify(stockBalanceRepository).findIssuableBalancesFefo(any(), eq(java.util.List.of(raw.getWarehouseId())), any(), any());
        // touch(): reservations are child rows, so without it the work order's @Version would not move (B112).
        assertThat(workOrder.getUpdatedAt()).isNotNull();
        verify(workOrderRepository).saveAndFlush(workOrder);
    }

    /** FE §3.3 release-reservations: every ACTIVE reservation goes back to free stock, no movement. */
    @Test
    void releaseAll_releasesTheUnissuedPartOfEveryActiveReservation_andMovesTheVersionOn() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        MaterialReservation reservation = reservation(workOrder, line, warehouse, new BigDecimal("6"));
        reservation.setConsumedQuantity(new BigDecimal("2"));
        StockBalance balance = StockBalance.builder().item(line.getComponentItem()).warehouse(warehouse)
                .quantity(new BigDecimal("10")).reservedQuantity(new BigDecimal("4")).build();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(reservationRepository.findByWorkOrderWorkOrderIdAndStatus(workOrder.getWorkOrderId(), MaterialReservationStatus.ACTIVE))
                .thenReturn(java.util.List.of(reservation));
        when(inventoryAvailabilityService.getStockBalanceForIssue(line.getComponentItem(), warehouse.getWarehouseId(), null))
                .thenReturn(balance);
        when(workOrderRepository.saveAndFlush(workOrder)).thenAnswer(invocation -> {
            workOrder.setVersion(1L);
            return workOrder;
        });

        var response = service.releaseAll(workOrder.getWorkOrderId(),
                new com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest(0L), null);

        assertThat(response.releasedReservationCount()).isEqualTo(1);
        assertThat(response.lines().get(0).releasedQuantity()).isEqualByComparingTo("4");
        assertThat(response.workOrderVersion()).isEqualTo(1L);
        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("0");
        assertThat(balance.getQuantity()).isEqualByComparingTo("10");
        assertThat(reservation.getStatus()).isEqualTo(MaterialReservationStatus.RELEASED);
    }

    @ParameterizedTest
    @EnumSource(value = WorkOrderStatus.class, names = {"CANCELLED", "CLOSED"})
    void releaseAll_onACancelledOrClosedWorkOrder_throwsStateConflict(WorkOrderStatus status) {
        WorkOrder workOrder = workOrder(status, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.releaseAll(workOrder.getWorkOrderId(),
                new com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest(0L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        verifyNoInteractions(stockBalanceRepository);
    }

    // ── CF-2 (B264): an AVAILABLE lot past its expiry is not reservable ───────────────────────────────

    @Test
    void reserve_onAnAvailableLotPastItsExpiry_isLotNotEligible_andNothingIsSaved() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.PLANNED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        InventoryLot lot = InventoryLot.builder().lotId(UUID.randomUUID()).item(line.getComponentItem())
                .lotCode("LOT-A").status(LotStatus.AVAILABLE).expiresAt(java.time.Instant.now().minusSeconds(1)).build();
        StockBalance balance = StockBalance.builder().item(line.getComponentItem()).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal("10")).reservedQuantity(BigDecimal.ZERO).build();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(reservationRepository.sumActiveRemainingByComponentLineId(line.getComponentLineId())).thenReturn(BigDecimal.ZERO);
        when(inventoryAvailabilityService.getStockBalanceForIssue(line.getComponentItem(), warehouse.getWarehouseId(),
                lot.getLotId())).thenReturn(balance);

        assertThatThrownBy(() -> service.reserve(workOrder.getWorkOrderId(), new MaterialReservationCreateRequest(
                line.getComponentLineId(), warehouse.getWarehouseId(), lot.getLotId(), new BigDecimal("4"), 0L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOT_NOT_ELIGIBLE));

        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("0");
        verifyNoInteractions(stockBalanceRepository, idempotencyRecordRepository);
        verify(reservationRepository, never()).save(any());
        verify(workOrderRepository, never()).saveAndFlush(any());
    }

    /** Positive control for the case above: the same lot one day before its expiry reserves normally. */
    @Test
    void reserve_onAnAvailableLotNotYetExpired_reserves() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.PLANNED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        InventoryLot lot = InventoryLot.builder().lotId(UUID.randomUUID()).item(line.getComponentItem())
                .lotCode("LOT-B").status(LotStatus.AVAILABLE)
                .expiresAt(java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS)).build();
        StockBalance balance = StockBalance.builder().item(line.getComponentItem()).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal("6")).reservedQuantity(BigDecimal.ZERO).build();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(reservationRepository.sumActiveRemainingByComponentLineId(line.getComponentLineId())).thenReturn(BigDecimal.ZERO);
        when(inventoryAvailabilityService.getStockBalanceForIssue(line.getComponentItem(), warehouse.getWarehouseId(),
                lot.getLotId())).thenReturn(balance);
        when(reservationRepository.save(any(MaterialReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reserve(workOrder.getWorkOrderId(), new MaterialReservationCreateRequest(
                line.getComponentLineId(), warehouse.getWarehouseId(), lot.getLotId(), new BigDecimal("6"), 0L), null);

        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("6");
        verify(reservationRepository).save(argThat(r -> r.getLot() == lot));
    }

    /**
     * FE fixture: needs 10, lot A (10, expired) is not offered by FEFO, lot B (6, expiring tomorrow) is. Automatic
     * reservation reserves 6 and stops — partial, never touching lot A. The query side of "never offers lot A" is
     * pinned against Postgres in StockBalanceRepositoryIT.feFixture_*; here the service must ask for it with the current
     * instant and must not reach for any other source.
     */
    @Test
    void reserveAutomatically_feFixture_reservesOnlyTheSixOnTheFreshLot_andAsksFefoAtTheCurrentInstant() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.PLANNED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        InventoryLot lotB = InventoryLot.builder().lotId(UUID.randomUUID()).item(line.getComponentItem())
                .lotCode("LOT-B").status(LotStatus.AVAILABLE)
                .expiresAt(java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS)).build();
        StockBalance six = StockBalance.builder().item(line.getComponentItem()).warehouse(warehouse).lot(lotB)
                .quantity(new BigDecimal("6")).reservedQuantity(BigDecimal.ZERO).build();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.resolveScope(ScopeResourceType.PLANT, workOrder.getPlant().getPlantId()))
                .thenReturn(new com.erp.manufacturing.module.organization.service.OrganizationScopeResolution(
                        ScopeResourceType.PLANT, workOrder.getPlant().getPlantId(), UUID.randomUUID(),
                        java.util.List.of(warehouse.getWarehouseId())));
        when(reservationRepository.sumActiveRemainingByComponentLineId(line.getComponentLineId())).thenReturn(BigDecimal.ZERO);
        when(stockBalanceRepository.findIssuableBalancesFefo(eq(line.getComponentItem().getItemId()),
                eq(java.util.List.of(warehouse.getWarehouseId())), eq(LotStatus.AVAILABLE), any(java.time.Instant.class)))
                .thenReturn(java.util.List.of(six));
        when(reservationRepository.save(any(MaterialReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);
        java.time.Instant before = java.time.Instant.now();

        var created = service.reserveAutomatically(workOrder.getWorkOrderId(),
                new com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest(0L), null);

        java.time.Instant after = java.time.Instant.now();
        assertThat(created).singleElement().satisfies(r -> assertThat(r.quantity()).isEqualByComparingTo("6"));
        assertThat(six.getReservedQuantity()).isEqualByComparingTo("6");
        org.mockito.ArgumentCaptor<java.time.Instant> now = org.mockito.ArgumentCaptor.forClass(java.time.Instant.class);
        verify(stockBalanceRepository).findIssuableBalancesFefo(any(), any(), any(), now.capture());
        assertThat(now.getValue()).isBetween(before, after);
        verify(reservationRepository, times(1)).save(argThat(r -> r.getLot() == lotB));
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
        workOrder.setVersion(0L);
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
