package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
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
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssuePostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    @Mock WorkOrderPermissionGuard workOrderPermissionGuard;
    @Mock UserLookupService userLookupService;
    @Mock WorkOrderCostAccumulatorService costAccumulatorService;

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
                workOrderPermissionGuard,
                support,
                new IdempotencySupport(new ObjectMapper()),
                new ManufacturingExecutionMapper(),
                new TraceIdProvider(),
                userLookupService,
                costAccumulatorService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Spec §4.2 "History: createdBy". Rule C14 requires the whole page to resolve in one query, and
     * rule C15 requires that to be asserted — a per-row lookup would still render correctly and only
     * show up as load.
     */
    @Test
    void list_resolvesTheAuthorUsernameInOneBatchQuery() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        UUID authorId = UUID.randomUUID();
        MaterialIssue first = issue(workOrder, authorId);
        MaterialIssue second = issue(workOrder, authorId);

        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(issueRepository.findByWorkOrderWorkOrderId(eq(workOrder.getWorkOrderId()), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(issueLineRepository.findByIssueIssueIdIn(any())).thenReturn(List.of());
        when(userLookupService.findUsernames(any())).thenReturn(Map.of(authorId, "storekeeper1"));

        var page = service.list(workOrder.getWorkOrderId(), PageRequest.of(0, 20));

        assertThat(page.content()).extracting(MaterialIssueResponse::createdByUsername)
                .containsExactly("storekeeper1", "storekeeper1");
        assertThat(page.content().get(0).workOrderCode()).isEqualTo(workOrder.getWorkOrderNo());
        verify(userLookupService, times(1)).findUsernames(any());
    }

    private MaterialIssue issue(WorkOrder workOrder, UUID createdBy) {
        MaterialIssue issue = MaterialIssue.builder()
                .issueId(UUID.randomUUID())
                .workOrder(workOrder)
                .status(MaterialIssueStatus.POSTED)
                .idempotencyKey(UUID.randomUUID().toString())
                .lines(new ArrayList<>())
                .build();
        issue.setCreatedBy(createdBy);
        return issue;
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
                        null,
                        new BigDecimal("4"),
                        "Issue reserved",
                        null))), "KEY-ISSUE");

        assertThat(reservation.getConsumedQuantity()).isEqualByComparingTo("4");
        assertThat(reservation.getStatus()).isEqualTo(MaterialReservationStatus.ACTIVE);
        assertThat(line.getIssuedQuantity()).isEqualByComparingTo("4");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(wipTransactionService).recordMaterialIssued(eq(workOrder), eq(new BigDecimal("4")), any());
    }

    @Test
    void post_serialTrackedComponent_threadsSerialIdIntoIssueCommandAndOntoLine() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        line.getComponentItem().setSerialTracked(true);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        UUID serialId = UUID.randomUUID();
        SerialNumber serial = SerialNumber.builder()
                .serialId(serialId)
                .item(line.getComponentItem())
                .serialCode("SN-1")
                .status(SerialStatus.ISSUED)
                .build();
        StockMovement movement = StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(line.getComponentItem())
                .warehouse(warehouse)
                .serial(serial)
                .movementType(MovementType.ISSUE)
                .direction(MovementDirection.OUT)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("KEY")
                .createdAt(Instant.now())
                .build();

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-SERIAL")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(movementService.issue(any(InventoryIssueCommand.class), eq("KEY-SERIAL:L1")))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(issueRepository.save(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialIssueLineRequest lineRequest = new MaterialIssueLineRequest(
                line.getComponentLineId(), null, warehouse.getWarehouseId(), null, null,
                serialId, BigDecimal.ONE, null, null);
        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(lineRequest)), "KEY-SERIAL");

        ArgumentCaptor<InventoryIssueCommand> commandCaptor = ArgumentCaptor.forClass(InventoryIssueCommand.class);
        verify(movementService).issue(commandCaptor.capture(), eq("KEY-SERIAL:L1"));
        assertThat(commandCaptor.getValue().serialId()).isEqualTo(serialId);

        ArgumentCaptor<MaterialIssue> issueCaptor = ArgumentCaptor.forClass(MaterialIssue.class);
        verify(issueRepository).save(issueCaptor.capture());
        assertThat(issueCaptor.getValue().getLines().get(0).getSerial().getSerialId()).isEqualTo(serialId);
    }

    @Test
    void post_serialTrackedComponentViaReservation_threadsSerialIdIntoIssueReservedCommand() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        line.getComponentItem().setSerialTracked(true);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        UUID serialId = UUID.randomUUID();
        MaterialReservation reservation = MaterialReservation.builder()
                .reservationId(UUID.randomUUID())
                .workOrder(workOrder)
                .componentLine(line)
                .item(line.getComponentItem())
                .warehouse(warehouse)
                .quantity(BigDecimal.ONE)
                .consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE)
                .build();
        StockMovement movement = movement(line.getComponentItem(), warehouse);

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-SERIAL-RES")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(reservationService.findActiveReservationForIssue(workOrder.getWorkOrderId(), reservation.getReservationId()))
                .thenReturn(reservation);
        when(movementService.issueReserved(any(InventoryIssueCommand.class), eq("KEY-SERIAL-RES:L1")))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(issueRepository.save(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialIssueLineRequest lineRequest = new MaterialIssueLineRequest(
                line.getComponentLineId(), reservation.getReservationId(), warehouse.getWarehouseId(), null, null,
                serialId, BigDecimal.ONE, null, null);
        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(lineRequest)), "KEY-SERIAL-RES");

        ArgumentCaptor<InventoryIssueCommand> commandCaptor = ArgumentCaptor.forClass(InventoryIssueCommand.class);
        verify(movementService).issueReserved(commandCaptor.capture(), eq("KEY-SERIAL-RES:L1"));
        assertThat(commandCaptor.getValue().serialId()).isEqualTo(serialId);
        assertThat(reservation.getConsumedQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void post_onBlockedWorkOrder_shouldThrow() {
        // B13: a BLOCKED work order must not be able to issue material.
        WorkOrder workOrder = workOrder(WorkOrderStatus.BLOCKED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(issueRepository.findWithLinesByIdempotencyKey("KEY-BLOCKED")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        UUID workOrderId = workOrder.getWorkOrderId();
        MaterialIssuePostRequest request = new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, BigDecimal.ONE, null)));

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-BLOCKED"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void issue_withinRemaining_noOverrideNeeded() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        StockMovement movement = movement(line.getComponentItem(), warehouse);

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-WITHIN")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(movementService.issue(any(InventoryIssueCommand.class), eq("KEY-WITHIN:L1")))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(issueRepository.save(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("10"), null))), "KEY-WITHIN");

        ArgumentCaptor<MaterialIssue> captor = ArgumentCaptor.forClass(MaterialIssue.class);
        verify(issueRepository).save(captor.capture());
        assertThat(captor.getValue().getLines().get(0).isOverIssue()).isFalse();
        verifyNoInteractions(workOrderPermissionGuard);
    }

    @Test
    void post_createdMovement_accumulatesMaterialCostExactlyOnce() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        StockMovement movement = movement(line.getComponentItem(), warehouse);

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-COST")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(movementService.issue(any(InventoryIssueCommand.class), eq("KEY-COST:L1")))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(issueRepository.save(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("4"), null))), "KEY-COST");

        verify(costAccumulatorService).accumulateMaterialCost(workOrder, line.getComponentItem(), new BigDecimal("4"));
    }

    @Test
    void postInternal_replayedIdempotencyKey_doesNotDoubleAccumulateMaterialCost() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        // payloadHash is null (B5c) — the builder never sets it — so ensureSamePayload() below
        // short-circuits without needing a matching hash; this test is only about accumulation.
        MaterialIssue existing = issue(workOrder, UUID.randomUUID());

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-REPLAY")).thenReturn(Optional.of(existing));

        service.postInternal(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of()), "KEY-REPLAY");

        verifyNoInteractions(costAccumulatorService);
    }

    @Test
    void issue_exceedRemaining_withoutPermission_shouldThrowAccessDenied() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        authenticate();

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-OVER")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_OVERRIDE"), eq(workOrder.getWorkOrderId())))
                .thenReturn(false);

        MaterialIssuePostRequest request = new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("99"), null)));
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-OVER"))
                .isInstanceOf(AccessDeniedException.class);

        verify(movementService, never()).issue(any(), anyString());
        verify(issueRepository, never()).save(any());
    }

    @Test
    void issue_exceedRemaining_withPermissionButBlankReason_shouldThrow() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        authenticate();

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-BLANK")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_OVERRIDE"), eq(workOrder.getWorkOrderId())))
                .thenReturn(true);

        MaterialIssuePostRequest request = new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("12"), "   ")));
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-BLANK"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.MISSING_REQUIRED_FIELD));

        verify(movementService, never()).issue(any(), anyString());
        verify(issueRepository, never()).save(any());
    }

    @Test
    void issue_exceedRemaining_withPermissionAndReason_shouldSucceed() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        StockMovement movement = movement(line.getComponentItem(), warehouse);
        authenticate();

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-APPROVED")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_OVERRIDE"), eq(workOrder.getWorkOrderId())))
                .thenReturn(true);
        when(movementService.issue(any(InventoryIssueCommand.class), eq("KEY-APPROVED:L1")))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(issueRepository.save(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("12"), " Scrap rework "))), "KEY-APPROVED");

        ArgumentCaptor<MaterialIssue> captor = ArgumentCaptor.forClass(MaterialIssue.class);
        verify(issueRepository).save(captor.capture());
        MaterialIssueLine savedLine = captor.getValue().getLines().get(0);
        assertThat(savedLine.isOverIssue()).isTrue();
        assertThat(savedLine.getOverrideReason()).isEqualTo("Scrap rework");
        // DB constraint has been relaxed to issued_quantity >= 0, so this is now persistable.
        assertThat(line.getIssuedQuantity()).isEqualByComparingTo("12");
        assertThat(line.getIssuedQuantity()).isGreaterThan(line.getRequiredQuantity());
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    private MaterialIssueLineRequest issueLine(WorkOrderComponentLine line,
                                               Warehouse warehouse,
                                               BigDecimal quantity,
                                               String overrideReason) {
        return new MaterialIssueLineRequest(
                line.getComponentLineId(),
                null,
                warehouse.getWarehouseId(),
                null,
                null,
                null,
                quantity,
                null,
                overrideReason);
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
