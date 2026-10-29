package com.erp.manufacturing.module.workorder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.module.workorder.repository.WorkOrderIdempotencyRecordRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.planning.service.WorkOrderPlanningLinkService;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostBaselineService;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.service.RoutingLookupService;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.mapper.WorkOrderMapper;
import com.erp.manufacturing.module.workorder.repository.ComponentQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionReceiptService;
import com.erp.manufacturing.module.workorder.service.execution.WipTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    @Mock RoutingLookupService routingLookupService;
    @Mock MaterialReservationService materialReservationService;
    @Mock MaterialIssueService materialIssueService;
    @Mock WipTransactionService wipTransactionService;
    @Mock ProductionReceiptService productionReceiptService;
    @Mock WorkOrderDemandAllocationService allocationService;
    @Mock MaterialReservationRepository reservationRepository;
    @Mock WorkOrderReleaseGate releaseGate;
    @Mock WorkCalendarLookupService workCalendarLookupService;
    @Mock MaterialIssueRepository materialIssueRepository;
    @Mock WorkOrderPlanningLinkService planningLinkService;
    @Mock WorkOrderIdempotencyRecordRepository idempotencyRecordRepository;
    @Mock SecurityAuditorAware auditorAware;
    @Mock ProductionCostBaselineService costBaselineService;

    WorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(
                workOrderRepository,
                organizationLookupService,
                itemLookupService,
                bomLookupService,
                routingLookupService,
                materialReservationService,
                materialIssueService,
                wipTransactionService,
                productionReceiptService,
                allocationService,
                reservationRepository,
                releaseGate,
                new WorkOrderScheduler(workCalendarLookupService),
                materialIssueRepository,
                planningLinkService,
                new WorkOrderIdempotency(idempotencyRecordRepository,
                        new IdempotencySupport(new ObjectMapper().findAndRegisterModules())),
                auditorAware,
                costBaselineService,
                new WorkOrderMapper());
    }

    /**
     * Spec §3.3 "Requirement" wants {@code reservedQuantity} on the work order itself, not only on
     * {@code /material-readiness}. Asserted here is the Java half: the batch result lands on the
     * matching line. That the query counts only {@code ACTIVE} reservations is JPQL and lives in
     * {@code MaterialReservationRepositoryIT} (rule R7).
     */
    @Test
    void get_mapsReservedQuantityOntoTheMatchingComponentLine() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        UUID componentLineId = workOrder.getComponentLines().get(0).getComponentLineId();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(reservationRepository.sumActiveRemainingByWorkOrderIds(any()))
                .thenReturn(List.of(componentQuantity(componentLineId, new BigDecimal("6"))));

        WorkOrderResponse response = service.get(workOrder.getWorkOrderId());

        assertThat(response.componentLines()).hasSize(1);
        assertThat(response.componentLines().get(0).reservedQuantity()).isEqualByComparingTo("6");
    }

    /**
     * A component line the aggregate returned no row for has nothing reserved — it must render as
     * {@code 0}, not {@code null}. {@code group by} never emits a zero row, so this is the default the
     * mapper has to supply.
     */
    @Test
    void get_componentLineWithoutAnyReservation_readsAsZeroNotNull() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(reservationRepository.sumActiveRemainingByWorkOrderIds(any())).thenReturn(List.of());

        WorkOrderResponse response = service.get(workOrder.getWorkOrderId());

        assertThat(response.componentLines().get(0).reservedQuantity()).isEqualByComparingTo("0");
    }

    /**
     * Rule C15: one aggregate for the whole page, never one per row. Getting this wrong produces
     * byte-identical output and only shows up as query load, which is why it has to be asserted
     * rather than eyeballed.
     */
    @Test
    void list_resolvesReservedQuantityInOneBatchQueryForThePage() {
        WorkOrder first = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrder second = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("20"));
        UUID plantId = first.getPlant().getPlantId();
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(first.getPlant());
        when(workOrderRepository.search(eq(plantId), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(allocationService.findByWorkOrderIds(any())).thenReturn(Map.of());
        when(reservationRepository.sumActiveRemainingByWorkOrderIds(any())).thenReturn(List.of(
                componentQuantity(first.getComponentLines().get(0).getComponentLineId(), new BigDecimal("4"))));

        var page = service.list(plantId, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().get(0).componentLines().get(0).reservedQuantity()).isEqualByComparingTo("4");
        assertThat(page.content().get(1).componentLines().get(0).reservedQuantity()).isEqualByComparingTo("0");
        verify(reservationRepository, times(1)).sumActiveRemainingByWorkOrderIds(any());
    }

    private ComponentQuantityProjection componentQuantity(UUID componentLineId, BigDecimal quantity) {
        return new ComponentQuantityProjection() {
            @Override public UUID getComponentLineId() { return componentLineId; }
            @Override public BigDecimal getQuantity() { return quantity; }
        };
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
        stubSaveReturningArgument();

        WorkOrderResponse response = service.create(plantId, new WorkOrderCreateRequest(
                " WO-001 ", productId, warehouseId, new BigDecimal("10"), null, null, " First run "));

        assertThat(response.workOrderNo()).isEqualTo("WO-001");
        assertThat(response.status()).isEqualTo(WorkOrderStatus.DRAFT.name());
        assertThat(response.componentLines()).hasSize(1);
        assertThat(response.componentLines().get(0).requiredQuantity()).isEqualByComparingTo("22");
        assertThat(response.notes()).isEqualTo("First run");
    }

    /**
     * CF-11 (B273): {@code required = planned × quantityPer × (1 + scrapRate)}, rounded HALF_UP to the
     * column's 6 decimals in Java, so the create response equals what Postgres stores and reads back.
     * Rows 4 and 5 carry a 7th digit: 1.2000012 → 1.200001 tells HALF_UP from CEILING (MRP's rounding,
     * which would give 1.200002), and 1.5000015 → 1.500002 tells it from HALF_DOWN / FLOOR.
     */
    @ParameterizedTest(name = "planned {0} x {1} x (1 + {2}) = {3}")
    @CsvSource({
            "10,  2,        0.100000, 22.000000",
            "5,   2,        0.100000, 11.000000",
            "10,  2,        0,        20.000000",
            "1.2, 1.000001, 0,        1.200001",
            "1.5, 1.000001, 0,        1.500002"
    })
    void create_requiredQuantityIsRoundedHalfUpToSixDecimals(String planned, String quantityPer, String scrapRate,
                                                             String expected) {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Plant plant = plant(plantId, companyId, OrganizationStatus.ACTIVE);
        Item product = item(productId, companyId, "FG-100", ItemType.FINISHED_GOOD, false);
        BomHeader bom = activeBom(product);
        bom.getLines().add(bomLine(bom, item(UUID.randomUUID(), companyId, "RM-001", ItemType.RAW_MATERIAL, false),
                10, quantityPer, scrapRate));
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant);
        when(workOrderRepository.existsByPlantPlantIdAndWorkOrderNo(plantId, "WO-001")).thenReturn(false);
        when(itemLookupService.getActiveItem(productId)).thenReturn(product);
        when(organizationLookupService.getActiveWarehouseInPlant(warehouseId, plantId))
                .thenReturn(warehouse(warehouseId, plant, OrganizationStatus.ACTIVE));
        when(bomLookupService.getActiveBom(companyId, productId)).thenReturn(bom);
        stubSaveReturningArgument();

        WorkOrderResponse response = service.create(plantId, new WorkOrderCreateRequest(
                "WO-001", productId, warehouseId, new BigDecimal(planned), null, null, null));

        ArgumentCaptor<WorkOrder> saved = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(saved.capture());
        BigDecimal stored = saved.getValue().getComponentLines().get(0).getRequiredQuantity();
        // isEqualTo, not isEqualByComparingTo: the scale is part of the contract.
        assertThat(stored).isEqualTo(new BigDecimal(expected));
        assertThat(stored.scale()).isEqualTo(6);
        assertThat(response.componentLines().get(0).requiredQuantity()).isEqualTo(new BigDecimal(expected));
    }

    /** CF-11: a PATCH of plannedQuantity on a draft recalculates with the same rounding as create. */
    @ParameterizedTest(name = "PATCH planned {0} x {1} x (1 + {2}) = {3}")
    @CsvSource({
            "5,   2,        0.100000, 11.000000",
            "1.2, 1.000001, 0,        1.200001"
    })
    void update_plannedQuantity_recalculatesRequiredQuantityWithTheSameRounding(String planned, String quantityPer,
                                                                                String scrapRate, String expected) {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        line.setQuantityPer(new BigDecimal(quantityPer));
        line.setScrapRate(new BigDecimal(scrapRate));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        WorkOrderResponse response = service.update(workOrder.getWorkOrderId(), new WorkOrderUpdateRequest(
                null, new BigDecimal(planned), null, null, null, 0L, null), null);

        assertThat(line.getRequiredQuantity()).isEqualTo(new BigDecimal(expected));
        assertThat(line.getRequiredQuantity().scale()).isEqualTo(6);
        assertThat(response.componentLines().get(0).requiredQuantity()).isEqualTo(new BigDecimal(expected));
    }

    // ── routing snapshot (F4) ──────────────────────────────────────────────

    @Test
    void createFromMrp_snapshotsActiveRoutingHeaderOntoWorkOrder() {
        Fixture fixture = fixtureWithBom();
        RoutingHeader routing = activeRouting(fixture.product(), "RT-FG100", "V1");
        when(routingLookupService.getActiveRouting(fixture.companyId(), fixture.productId()))
                .thenReturn(routing);
        stubSaveReturningArgument();

        WorkOrderResponse response = service.createFromMrp(fixture.plantId(), createRequest(fixture), null, null);

        assertThat(response.sourceRoutingId()).isEqualTo(routing.getRoutingId());
        assertThat(response.sourceRoutingCode()).isEqualTo("RT-FG100");
        assertThat(response.sourceRoutingVersion()).isEqualTo("V1");
        assertThat(response.routingCapturedAt()).isNotNull();
    }

    /**
     * Invariant B49: the snapshot is a copy. Revising the routing master after the work order
     * exists must not retroactively change what the shop floor was told to build.
     */
    @Test
    void createFromMrp_routingMasterRevisedAfterwards_workOrderSnapshotStaysFrozen() {
        Fixture fixture = fixtureWithBom();
        RoutingHeader routing = activeRouting(fixture.product(), "RT-FG100", "V1");
        when(routingLookupService.getActiveRouting(fixture.companyId(), fixture.productId()))
                .thenReturn(routing);
        stubSaveReturningArgument();

        service.createFromMrp(fixture.plantId(), createRequest(fixture), null, null);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(captor.capture());
        WorkOrder saved = captor.getValue();

        routing.setCode("RT-FG100-REVISED");
        routing.setRoutingVersion("V2");
        routing.getOperations().clear();

        assertThat(saved.getSourceRoutingCode()).isEqualTo("RT-FG100");
        assertThat(saved.getSourceRoutingVersion()).isEqualTo("V1");
        assertThat(saved.getSourceRoutingId()).isEqualTo(routing.getRoutingId());
    }

    @Test
    void createFromMrp_itemWithoutActiveRouting_failsWithMissingRouting() {
        Fixture fixture = fixtureWithBom();
        when(routingLookupService.getActiveRouting(fixture.companyId(), fixture.productId()))
                .thenThrow(ExceptionFactory.businessRule(BusinessErrorCode.MISSING_ROUTING,
                        "No ACTIVE routing for item " + fixture.productId()));

        assertThatThrownBy(() -> service.createFromMrp(fixture.plantId(), createRequest(fixture), null, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.MISSING_ROUTING));

        verify(workOrderRepository, never()).saveAndFlush(any());
    }

    /**
     * F4 only blocks the MRP proposal path (NEXT_PHASE_PLAN F4 §1.3): manual creation still works
     * for items that have no routing yet, and simply carries an empty snapshot.
     */
    @Test
    void create_manualWithoutActiveRouting_succeedsWithEmptyRoutingSnapshot() {
        Fixture fixture = fixtureWithBom();
        when(routingLookupService.findActiveRouting(fixture.companyId(), fixture.productId()))
                .thenReturn(Optional.empty());
        stubSaveReturningArgument();

        WorkOrderResponse response = service.create(fixture.plantId(), createRequest(fixture));

        assertThat(response.sourceRoutingId()).isNull();
        assertThat(response.sourceRoutingCode()).isNull();
        assertThat(response.sourceRoutingVersion()).isNull();
        assertThat(response.routingCapturedAt()).isNull();
        verify(routingLookupService, never()).getActiveRouting(any(), any());
    }

    @Test
    void create_manualWithActiveRouting_snapshotsItAnyway() {
        Fixture fixture = fixtureWithBom();
        RoutingHeader routing = activeRouting(fixture.product(), "RT-FG100", "V3");
        when(routingLookupService.findActiveRouting(fixture.companyId(), fixture.productId()))
                .thenReturn(Optional.of(routing));
        stubSaveReturningArgument();

        WorkOrderResponse response = service.create(fixture.plantId(), createRequest(fixture));

        assertThat(response.sourceRoutingCode()).isEqualTo("RT-FG100");
        assertThat(response.sourceRoutingVersion()).isEqualTo("V3");
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

        // Master-data validation, not a state machine transition — deliberately left at 422 when
        // F5 moved the work order state errors to STATE_CONFLICT (409).
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
                new WorkOrderUpdateRequest(null, new BigDecimal("12"), null, null, null, 0L, null), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    @Test
    void release_allComponentsFullyReserved_shouldRelease() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.RELEASED);
        assertThat(workOrder.getReleasedAt()).isNotNull();
        verify(releaseGate).ensureMaterialReady(workOrder.getWorkOrderId());
        verify(wipTransactionService).recordStart(workOrder);
    }

    @Test
    void release_partialReservation_shouldBlockAndThrow() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        // The gate persists BLOCKED in its own transaction, then refuses the release.
        doAnswer(invocation -> {
            workOrder.block(Instant.now(), "1/1 components short on reservation");
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Cannot release: 1 component(s) not fully reserved");
        }).when(releaseGate).ensureMaterialReady(workOrder.getWorkOrderId());

        assertThatThrownBy(() -> service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.BLOCKED);
        assertThat(workOrder.getBlockReason()).isNotBlank();
        verify(wipTransactionService, never()).recordStart(any());
    }

    @Test
    void release_fromBlockedAfterReservationCompleted_shouldRelease() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        workOrder.block(Instant.now(), "1/1 components short on reservation");
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.RELEASED);
        assertThat(workOrder.getBlockedAt()).isNull();
        assertThat(workOrder.getBlockReason()).isNull();
    }

    @Test
    void release_completedWorkOrder_shouldThrowBeforeGate() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.COMPLETED, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(releaseGate);
    }

    /**
     * C2-8 decision #1: the schedule is generated at release, sequentially, per operation. No
     * calendar on the work center ⇒ continuous time, no gaps skipped, and the calendar lookup is
     * never consulted (nothing to consult).
     */
    @Test
    void release_schedulesOperationsSequentially_continuousTimeWhenWorkCenterHasNoCalendar() {
        Instant anchor = Instant.parse("2026-08-10T08:00:00Z");
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        workOrder.setPlannedStartAt(anchor);
        WorkCenter workCenter = workCenter(workOrder.getPlant(), null);
        workOrder.getOperations().add(operation(workOrder, 1, workCenter, "5", "2"));
        workOrder.getOperations().add(operation(workOrder, 2, workCenter, "0", "1"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null);

        WorkOrderOperation first = workOrder.getOperations().get(0);
        WorkOrderOperation second = workOrder.getOperations().get(1);
        // duration = setupMinutes + runMinutesPerUnit * plannedQuantity(10)
        assertThat(first.getPlannedStartAt()).isEqualTo(anchor);
        assertThat(first.getPlannedEndAt()).isEqualTo(anchor.plusSeconds(25 * 60));
        assertThat(second.getPlannedStartAt()).isEqualTo(first.getPlannedEndAt());
        assertThat(second.getPlannedEndAt()).isEqualTo(second.getPlannedStartAt().plusSeconds(10 * 60));
        verifyNoInteractions(workCalendarLookupService);
    }

    /**
     * When the operation's Work Center has a calendar, release walks its working intervals instead of adding
     * minutes directly. CF-5 (R10): before CF-5 this case stubbed {@code computeEndInstant} and used the returned
     * end verbatim; release now walks {@code computeWorkingIntervals} through {@code CapacityPlacement} (the plant
     * preview's walk), and the operation starts at the first working moment rather than at the anchor.
     */
    @Test
    void release_schedulesOperations_walksTheWorkCenterCalendarWhenPresent() {
        Instant anchor = Instant.parse("2026-08-10T08:00:00Z");
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        workOrder.setPlannedStartAt(anchor);
        WorkCalendar calendar = WorkCalendar.builder()
                .workCalendarId(UUID.randomUUID())
                .plant(workOrder.getPlant())
                .code("CAL-1")
                .name("Calendar 1")
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .status(OrganizationStatus.ACTIVE)
                .build();
        WorkCenter workCenter = workCenter(workOrder.getPlant(), calendar);
        workOrder.getOperations().add(operation(workOrder, 1, workCenter, "5", "2"));
        // The calendar has nothing until two days later (a skipped weekend).
        Instant firstWorkingMoment = anchor.plus(2, java.time.temporal.ChronoUnit.DAYS);
        when(workCalendarLookupService.computeWorkingIntervals(
                eq(calendar.getWorkCalendarId()), eq(anchor), any(), any()))
                .thenReturn(List.of(new com.erp.manufacturing.module.shift.service.TimeInterval(
                        firstWorkingMoment, firstWorkingMoment.plusSeconds(8 * 3600))));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null);

        WorkOrderOperation operation = workOrder.getOperations().get(0);
        assertThat(operation.getPlannedStartAt()).isEqualTo(firstWorkingMoment);
        assertThat(operation.getPlannedEndAt()).isEqualTo(firstWorkingMoment.plusSeconds(25 * 60));
        verify(workCalendarLookupService, never()).computeEndInstant(any(), any(), any(), anyLong());
    }

    /**
     * CF-5 (B269): release divides by the work center's capacityUnits and captures what it used on the operation
     * — units, capacity minutes (setup 5 + run 2 × 10 = 25) and working minutes (25 / 2 = 12.5) — so the schedule
     * stays explainable if the work center changes later. The response shows the same three figures.
     */
    @Test
    void release_persistsTheCapturedCapacityOfEachSlot_andTheResponseShowsIt() {
        Instant anchor = Instant.parse("2026-08-10T08:00:00Z");
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        workOrder.setPlannedStartAt(anchor);
        WorkCenter workCenter = workCenter(workOrder.getPlant(), null);
        workCenter.setCapacityUnits(2);
        workOrder.getOperations().add(operation(workOrder, 1, workCenter, "5", "2"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        WorkOrderResponse response = service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null);

        WorkOrderOperation operation = workOrder.getOperations().get(0);
        assertThat(operation.getCapturedCapacityUnits()).isEqualTo(2);
        assertThat(operation.getRequiredResourceMinutes()).isEqualByComparingTo("25");
        assertThat(operation.getActiveElapsedMinutes()).isEqualByComparingTo("12.5");
        assertThat(operation.getPlannedEndAt()).isEqualTo(anchor.plusSeconds(750));
        WorkOrderOperationResponse shown = response.operations().get(0);
        assertThat(shown.capturedCapacityUnits()).isEqualTo(2);
        assertThat(shown.requiredResourceMinutes()).isEqualByComparingTo("25");
        assertThat(shown.activeElapsedMinutes()).isEqualByComparingTo("12.5");
        assertThat(shown.workMinutes()).isEqualTo(25L);
    }

    /** When the work order has no {@code plannedStartAt}, the first operation anchors at "now". */
    @Test
    void release_withoutAnExplicitPlannedStartAt_anchorsTheFirstOperationAtNow() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        WorkCenter workCenter = workCenter(workOrder.getPlant(), null);
        workOrder.getOperations().add(operation(workOrder, 1, workCenter, "0", "0"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        Instant before = Instant.now();
        service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null);
        Instant after = Instant.now();

        Instant scheduledStart = workOrder.getOperations().get(0).getPlannedStartAt();
        assertThat(scheduledStart).isBetween(before, after);
    }

    @Test
    void cancel_blockedWorkOrder_shouldCancel() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        workOrder.block(Instant.now(), "1/1 components short on reservation");
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        service.cancel(workOrder.getWorkOrderId(), new WorkOrderCancelRequest("Customer withdrew the order", 0L), null);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(workOrder.getCancelledAt()).isNotNull();
        // Spec §3.2 (F7): the reason is persisted, not just accepted and dropped.
        assertThat(workOrder.getCancelReason()).isEqualTo("Customer withdrew the order");
    }

    /**
     * Spec §3.2 requires a reason. It is checked before the status gate on purpose (C9), so a caller
     * who forgot it is told that rather than being told the work order is in the wrong state.
     */
    @Test
    void cancel_withoutReason_failsBeforeTouchingAnything() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.cancel(workOrderId, new WorkOrderCancelRequest("   ", 0L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.APPROVAL_REASON_REQUIRED));

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.DRAFT);
        verify(workOrderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(materialReservationService);
    }

    /** G8b (FE Q33): a cancel reason is 5–2000 characters after trimming; a short one names the field. */
    @Test
    void cancel_withATooShortReason_failsOnTheReasonFieldBeforeTouchingAnything() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.cancel(workOrderId, new WorkOrderCancelRequest(" bad ", 0L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.INVALID_INPUT));

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.DRAFT);
        verify(workOrderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(materialReservationService);
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

        UUID workOrderId = workOrder.getWorkOrderId();
        assertThatThrownBy(() -> service.cancel(workOrderId, new WorkOrderCancelRequest("Scrapped", 0L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    /**
     * P6: closing is also the "Reconcile" step — any material still sitting in an ACTIVE reservation
     * (over-reserved component never issued) must be released back to available stock, because once
     * CLOSED the work order can never be touched again to free it any other way.
     */
    @Test
    void close_completedWorkOrder_succeedsAndReleasesActiveReservations() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.COMPLETED, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        service.close(workOrder.getWorkOrderId());

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(workOrder.getClosedAt()).isNotNull();
        verify(materialReservationService).cancelActiveReservations(workOrder);
    }

    /** G11c (D5): a work order released with a cost baseline must close through finalize-and-close. */
    @Test
    void close_workOrderWithCostBaseline_isCloseBlocked_andChangesNothing() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.COMPLETED, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(costBaselineService.hasBaseline(workOrder.getWorkOrderId())).thenReturn(true);
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.close(workOrderId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CLOSE_BLOCKED));

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        verify(workOrderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(materialReservationService);
    }

    /** G11a (FE R05): the cost baseline is captured in the release itself, once. */
    @Test
    void release_capturesTheCostBaselineOnce() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        workOrder.getOperations().add(operation(workOrder, 1, workCenter(workOrder.getPlant(), null), "5", "2"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null);

        verify(costBaselineService, times(1)).capture(eq(workOrder), any());
    }

    @Test
    void close_notCompletedWorkOrder_throwsStateConflictBeforeTouchingAnything() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.close(workOrderId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.RELEASED);
        verify(workOrderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(materialReservationService);
    }

    @Test
    void close_alreadyClosedWorkOrder_throwsStateConflict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.CLOSED, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.close(workOrderId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        verifyNoInteractions(materialReservationService);
    }

    // ── G8a ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void update_staleExpectedVersion_throwsConcurrentModificationBeforeAnyWrite() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.update(workOrder.getWorkOrderId(),
                new WorkOrderUpdateRequest(null, new BigDecimal("12"), null, null, null, 4L, null), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));

        assertThat(workOrder.getPlannedQuantity()).isEqualByComparingTo("10");
        verify(workOrderRepository, never()).saveAndFlush(any());
    }

    /** FE Q7: what the planning quota and the sales order allocation were sized on cannot change. */
    @Test
    void update_sourceLinkedDraft_newQuantityOrOutputWarehouse_throwsSourceLockedPerField() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        workOrder.setPlanningProposalId(UUID.randomUUID());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.update(workOrder.getWorkOrderId(), new WorkOrderUpdateRequest(
                UUID.randomUUID(), new BigDecimal("12"), null, null, null, 0L, null), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    MultiErrorException error = (MultiErrorException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(BusinessErrorCode.SOURCE_LOCKED);
                    assertThat(error.getFieldErrors()).containsOnlyKeys("plannedQuantity", "outputWarehouseId");
                });
        verify(workOrderRepository, never()).saveAndFlush(any());
    }

    /** Sending the locked fields back unchanged is not a change — notes and dates stay editable. */
    @Test
    void update_sourceLinkedDraft_sameQuantity_isAllowed_andMovesTheVersionOn() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        workOrder.setPlanningProposalId(UUID.randomUUID());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);
        Instant before = workOrder.getUpdatedAt();

        service.update(workOrder.getWorkOrderId(), new WorkOrderUpdateRequest(
                null, new BigDecimal("10.000000"), null, null, "Rush", 0L, null), null);

        assertThat(workOrder.getNotes()).isEqualTo("Rush");
        // touch(): the row is dirty even when nothing scalar changed, so @Version moves on (B112).
        assertThat(workOrder.getUpdatedAt()).isNotEqualTo(before);
        verify(workOrderRepository).saveAndFlush(workOrder);
    }

    /** D2: keyed by component item, applied to every line of that item. */
    @Test
    void update_componentWarehouses_setsTheWarehouseOnEveryLineOfThatItem() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        WorkOrderComponentLine first = workOrder.getComponentLines().get(0);
        WorkOrderComponentLine second = WorkOrderComponentLine.builder()
                .componentLineId(UUID.randomUUID()).workOrder(workOrder).bomLine(first.getBomLine())
                .componentItem(first.getComponentItem()).lineNo(20).quantityPer(BigDecimal.ONE)
                .scrapRate(BigDecimal.ZERO).requiredQuantity(BigDecimal.TEN).issuedQuantity(BigDecimal.ZERO).build();
        workOrder.getComponentLines().add(second);
        Warehouse raw = warehouse(UUID.randomUUID(), workOrder.getPlant(), OrganizationStatus.ACTIVE);
        UUID itemId = first.getComponentItem().getItemId();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(raw.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(raw);
        when(reservationRepository.findByWorkOrderWorkOrderIdAndStatus(any(), any())).thenReturn(List.of());
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        WorkOrderResponse response = service.update(workOrder.getWorkOrderId(), new WorkOrderUpdateRequest(
                null, null, null, null, null, 0L, List.of(new ComponentWarehouseRequest(itemId, raw.getWarehouseId()))), null);

        assertThat(first.getWarehouse()).isSameAs(raw);
        assertThat(second.getWarehouse()).isSameAs(raw);
        assertThat(response.componentLines()).extracting(WorkOrderComponentLineResponse::warehouseId)
                .containsOnly(raw.getWarehouseId());
    }

    @Test
    void update_componentWarehouseForAnItemThatIsNotAComponent_isRejectedOnItsField() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.update(workOrder.getWorkOrderId(), new WorkOrderUpdateRequest(
                null, null, null, null, null, 0L,
                List.of(new ComponentWarehouseRequest(UUID.randomUUID(), UUID.randomUUID()))), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors())
                        .containsOnlyKeys("componentWarehouses[0].itemId"));
        verifyNoInteractions(organizationLookupService);
    }

    /** Stock already held in another warehouse would sit outside the one warehouse the line now claims. */
    @Test
    void update_componentWarehouse_whileReservedInAnotherWarehouse_throwsStateConflict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse target = warehouse(UUID.randomUUID(), workOrder.getPlant(), OrganizationStatus.ACTIVE);
        Warehouse elsewhere = warehouse(UUID.randomUUID(), workOrder.getPlant(), OrganizationStatus.ACTIVE);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(target.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(target);
        when(reservationRepository.findByWorkOrderWorkOrderIdAndStatus(workOrder.getWorkOrderId(),
                com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus.ACTIVE))
                .thenReturn(List.of(com.erp.manufacturing.module.workorder.domain.MaterialReservation.builder()
                        .workOrder(workOrder).componentLine(line).item(line.getComponentItem()).warehouse(elsewhere)
                        .quantity(new BigDecimal("4")).consumedQuantity(BigDecimal.ZERO)
                        .status(com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.update(workOrder.getWorkOrderId(), new WorkOrderUpdateRequest(
                null, null, null, null, null, 0L,
                List.of(new ComponentWarehouseRequest(line.getComponentItem().getItemId(), target.getWarehouseId()))), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        assertThat(line.getWarehouse()).isNull();
    }

    @Test
    void plan_withoutAVersion_isRefusedNamingTheField() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.plan(workOrder.getWorkOrderId(), null, null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors())
                        .containsOnlyKeys("expectedWorkOrderVersion"));
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.DRAFT);
    }

    /** D9: release records which schedule it committed — revision, calendar fingerprint, plant zone. */
    @Test
    void release_commitsScheduleRevisionCalendarRevisionAndTimeZone() {
        Instant anchor = Instant.parse("2026-08-10T08:00:00Z");
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        workOrder.setPlannedStartAt(anchor);
        WorkCalendar calendar = WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(workOrder.getPlant())
                .code("CAL").name("Cal").effectiveFrom(LocalDate.of(2026, 1, 1)).status(OrganizationStatus.ACTIVE).build();
        workOrder.getOperations().add(operation(workOrder, 1, workCenter(workOrder.getPlant(), calendar), "5", "2"));
        when(workCalendarLookupService.computeWorkingIntervals(any(), any(), any(), any())).thenReturn(List.of(
                new com.erp.manufacturing.module.shift.service.TimeInterval(anchor, anchor.plusSeconds(3600))));
        when(workCalendarLookupService.calendarRevision(Set.of(calendar.getWorkCalendarId()))).thenReturn("0123456789abcdef");
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        WorkOrderResponse response = service.release(workOrder.getWorkOrderId(), new WorkOrderVersionRequest(0L), null);

        assertThat(response.scheduleRevision()).isEqualTo(1);
        assertThat(response.calendarRevision()).isEqualTo("0123456789abcdef");
        assertThat(response.scheduleTimeZone()).isEqualTo(workOrder.getPlant().getTimezone());
        assertThat(response.scheduleCapturedAt()).isNotNull();
        assertThat(response.operations().get(0).workMinutes()).isEqualTo(25L);
    }

    /** FE R06: a decision still waiting for a manager is activity — nothing is released. */
    @Test
    void cancel_withAnIssuePendingApproval_throwsStateConflictAndReleasesNothing() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(materialIssueRepository.existsByWorkOrderWorkOrderIdAndStatus(workOrder.getWorkOrderId(),
                com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus.PENDING_APPROVAL)).thenReturn(true);

        assertThatThrownBy(() -> service.cancel(workOrder.getWorkOrderId(),
                new WorkOrderCancelRequest("No longer needed", 0L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        verifyNoInteractions(materialReservationService, planningLinkService);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.RELEASED);
    }

    /** FE R06/Q21: the planning quota comes back with the cancel, carrying the reason. */
    @Test
    void cancel_unstartedWorkOrder_releasesItsPlanningQuotaWithTheReason() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.PLANNED, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);
        UUID actor = UUID.randomUUID();
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(actor));

        service.cancel(workOrder.getWorkOrderId(), new WorkOrderCancelRequest("Customer withdrew", 0L), null);

        verify(planningLinkService).releaseWorkOrder(eq(workOrder.getWorkOrderId()), eq("Customer withdrew"),
                any(Instant.class), eq(actor));
        verify(materialReservationService).cancelActiveReservations(workOrder);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    /** The reason is still checked first (C9); the version right after it. */
    @Test
    void cancel_withAReasonButNoVersion_isRefusedNamingTheVersionField() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.cancel(workOrder.getWorkOrderId(),
                new WorkOrderCancelRequest("No longer needed"), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors())
                        .containsOnlyKeys("expectedWorkOrderVersion"));
        verifyNoInteractions(planningLinkService);
    }

    /** Plant-local days: 15 Oct in Asia/Ho_Chi_Minh starts at 14 Oct 17:00 UTC; the end is exclusive. */
    @Test
    void list_dateRange_isTurnedIntoPlantLocalHalfOpenInstants() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        Plant plant = workOrder.getPlant();
        plant.setTimezone("Asia/Ho_Chi_Minh");
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(workOrderRepository.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list(plant.getPlantId(), null, null, null, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15),
                PageRequest.of(0, 20));

        verify(workOrderRepository).search(eq(plant.getPlantId()), isNull(), isNull(), isNull(),
                eq(Instant.parse("2026-10-14T17:00:00Z")), eq(Instant.parse("2026-10-15T17:00:00Z")), any());
    }

    @Test
    void list_toDateBeforeFromDate_isRefusedBeforeQuerying() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.DRAFT, new BigDecimal("10"));
        when(organizationLookupService.getActivePlant(workOrder.getPlant().getPlantId())).thenReturn(workOrder.getPlant());

        assertThatThrownBy(() -> service.list(workOrder.getPlant().getPlantId(), null, null, null,
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 14), PageRequest.of(0, 20)))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("toDate"));
        verify(workOrderRepository, never()).search(any(), any(), any(), any(), any(), any(), any());
    }

    /** R22: a retried plan with the same key answers with the current work order and writes nothing. */
    @Test
    void plan_replayedKeyWithSamePayload_returnsTheCurrentWorkOrderWithoutPlanningAgain() throws Exception {
        WorkOrder workOrder = workOrder(WorkOrderStatus.PLANNED, new BigDecimal("10"));
        // The first plan moved the version on; the retried body still carries the old one. The replay must
        // be recognised before the version check, or a network retry would be answered 409.
        workOrder.setVersion(1L);
        WorkOrderPlanRequest request = new WorkOrderPlanRequest(0L, null);
        IdempotencySupport support = new IdempotencySupport(new ObjectMapper().findAndRegisterModules());
        String hash = support.payloadHash(new WorkOrderIdempotency.Fingerprint(workOrder.getWorkOrderId(), request));
        when(idempotencyRecordRepository.findByIdempotencyKeyAndCommand("PLAN-1",
                com.erp.manufacturing.module.workorder.domain.WorkOrderCommand.WORK_ORDER_PLAN))
                .thenReturn(Optional.of(com.erp.manufacturing.module.workorder.domain.WorkOrderIdempotencyRecord.builder()
                        .idempotencyKey("PLAN-1").resourceId(workOrder.getWorkOrderId()).payloadHash(hash).build()));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        WorkOrderResponse response = service.plan(workOrder.getWorkOrderId(), request, "PLAN-1");

        assertThat(response.status()).isEqualTo("PLANNED");
        verify(workOrderRepository, never()).saveAndFlush(any());
        verify(idempotencyRecordRepository, never()).saveAndFlush(any());
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

    private WorkCenter workCenter(Plant plant, WorkCalendar calendar) {
        return WorkCenter.builder()
                .workCenterId(UUID.randomUUID())
                .plant(plant)
                .code("WC-1")
                .name("Work Center 1")
                .capacityUnitType(CapacityUnitType.MACHINE)
                .capacityUnits(1)
                .status(OrganizationStatus.ACTIVE)
                .workCalendar(calendar)
                .build();
    }

    private WorkOrderOperation operation(WorkOrder workOrder, int sequence, WorkCenter workCenter,
                                          String setupMinutes, String runMinutesPerUnit) {
        return WorkOrderOperation.builder()
                .workOrderOperationId(UUID.randomUUID())
                .workOrder(workOrder)
                .sequence(sequence)
                .name("Operation " + sequence)
                .workCenterCode(workCenter.getCode())
                .workCenter(workCenter)
                .setupMinutes(new BigDecimal(setupMinutes))
                .runMinutesPerUnit(new BigDecimal(runMinutesPerUnit))
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

    /** The lookups {@code createInternal} performs before it reaches the routing snapshot. */
    private record Fixture(UUID companyId, UUID plantId, UUID productId, UUID warehouseId, Item product) {}

    private Fixture fixtureWithBom() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Plant plant = plant(plantId, companyId, OrganizationStatus.ACTIVE);
        Item product = item(productId, companyId, "FG-100", ItemType.FINISHED_GOOD, false);
        BomHeader bom = activeBom(product);
        bom.getLines().add(bomLine(bom, item(UUID.randomUUID(), companyId, "RM-001", ItemType.RAW_MATERIAL, false),
                10, "2", "0.100000"));

        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant);
        when(workOrderRepository.existsByPlantPlantIdAndWorkOrderNo(plantId, "WO-001")).thenReturn(false);
        when(itemLookupService.getActiveItem(productId)).thenReturn(product);
        when(organizationLookupService.getActiveWarehouseInPlant(warehouseId, plantId))
                .thenReturn(warehouse(warehouseId, plant, OrganizationStatus.ACTIVE));
        when(bomLookupService.getActiveBom(companyId, productId)).thenReturn(bom);
        return new Fixture(companyId, plantId, productId, warehouseId, product);
    }

    private WorkOrderCreateRequest createRequest(Fixture fixture) {
        return new WorkOrderCreateRequest("WO-001", fixture.productId(), fixture.warehouseId(),
                new BigDecimal("10"), null, null, null);
    }

    /**
     * Mirrors what the real repository does on insert: the entity comes back with an id. The
     * service reads that id straight afterwards to look up the work order's demand allocations
     * (F6), so a stub that left it null would test a state that cannot occur.
     */
    private void stubSaveReturningArgument() {
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            if (workOrder.getWorkOrderId() == null) {
                workOrder.setWorkOrderId(UUID.randomUUID());
            }
            return workOrder;
        });
    }

    private RoutingHeader activeRouting(Item product, String code, String version) {
        RoutingHeader routing = RoutingHeader.builder()
                .routingId(UUID.randomUUID())
                .company(product.getCompany())
                .item(product)
                .code(code)
                .routingVersion(version)
                .status(RoutingStatus.ACTIVE)
                .operations(new ArrayList<>())
                .build();
        routing.getOperations().add(RoutingOperation.builder()
                .routingOperationId(UUID.randomUUID())
                .routing(routing)
                .sequence(10)
                .name("Assembly")
                .workCenter(com.erp.manufacturing.module.workcenter.domain.WorkCenter.builder()
                        .workCenterId(UUID.randomUUID())
                        .plant(Plant.builder().plantId(UUID.randomUUID()).code("PLANT").name("Plant")
                                .status(OrganizationStatus.ACTIVE).build())
                        .code("WC-01")
                        .name("WC-01")
                        .capacityUnitType(com.erp.manufacturing.module.workcenter.domain.CapacityUnitType.MACHINE)
                        .capacityUnits(1)
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .setupMinutes(new BigDecimal("15"))
                .runMinutesPerUnit(new BigDecimal("2.5"))
                .build());
        return routing;
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
