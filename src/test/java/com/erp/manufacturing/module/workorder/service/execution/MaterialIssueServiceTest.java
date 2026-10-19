package com.erp.manufacturing.module.workorder.service.execution;

import org.springframework.test.util.ReflectionTestUtils;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.repository.WorkOrderIdempotencyRecordRepository;
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
import com.erp.manufacturing.module.inventory.service.InventoryLotLookupService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueVersionedDecisionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueLineResponse;
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
    @Mock InventoryLotLookupService inventoryLotLookupService;
    @Mock WorkOrderIdempotencyRecordRepository idempotencyRecordRepository;

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
                costAccumulatorService,
                inventoryLotLookupService,
                new WorkOrderIdempotency(idempotencyRecordRepository,
                        new IdempotencySupport(new ObjectMapper().findAndRegisterModules())));
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
        when(issueRepository.findByWorkOrder(eq(workOrder.getWorkOrderId()), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(issueLineRepository.findByIssueIssueIdIn(any())).thenReturn(List.of());
        when(userLookupService.findUsernames(any())).thenReturn(Map.of(authorId, "storekeeper1"));

        var page = service.list(workOrder.getWorkOrderId(), null, PageRequest.of(0, 20));

        assertThat(page.content()).extracting(MaterialIssueResponse::createdByUsername)
                .containsExactly("storekeeper1", "storekeeper1");
        assertThat(page.content().get(0).workOrderCode()).isEqualTo(workOrder.getWorkOrderNo());
        verify(userLookupService, times(1)).findUsernames(any());
    }

    /**
     * A PENDING_APPROVAL line has no {@code lot} — that link is only made when approval posts the
     * movement — so the code has to come from the batch map, and it has to be <b>one</b> query for
     * the whole page (rules C14/C15). Resolving per row would render identically and only show up as
     * load, which is exactly the N+1 the approval queue was meant to avoid.
     */
    @Test
    void list_pendingLineThatNamedItsLotById_resolvesTheLotCodeInOneBatchQuery() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderComponentLine componentLine = workOrder.getComponentLines().get(0);
        UUID lotId = UUID.randomUUID();
        MaterialIssue first = pendingIssueNamingLotById(workOrder, componentLine, lotId);
        MaterialIssue second = pendingIssueNamingLotById(workOrder, componentLine, lotId);

        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(issueRepository.findByWorkOrder(eq(workOrder.getWorkOrderId()), any(), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(issueLineRepository.findByIssueIssueIdIn(any()))
                .thenReturn(List.of(first.getLines().get(0), second.getLines().get(0)));
        when(userLookupService.findUsernames(any())).thenReturn(Map.of());
        when(inventoryLotLookupService.findLotCodes(any())).thenReturn(Map.of(lotId, "LOT-A"));

        var page = service.list(workOrder.getWorkOrderId(), MaterialIssueStatus.PENDING_APPROVAL,
                PageRequest.of(0, 20));

        assertThat(page.content()).flatExtracting(MaterialIssueResponse::lines)
                .extracting(MaterialIssueLineResponse::lotNumber)
                .containsExactly("LOT-A", "LOT-A");
        assertThat(page.content().get(0).lines().get(0).lotId()).isEqualTo(lotId);
        verify(inventoryLotLookupService, times(1)).findLotCodes(any());
    }

    /**
     * A line that typed its lot code needs nothing looked up, and neither does a posted line that is
     * already linked to a lot. Asking anyway would put a query behind every page of ordinary issue
     * history for no gain.
     */
    @Test
    void list_lineThatAlreadyKnowsItsLotCode_asksTheLookupForNothing() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderComponentLine componentLine = workOrder.getComponentLines().get(0);
        MaterialIssue issue = pendingIssueNamingLotById(workOrder, componentLine, null);
        issue.getLines().get(0).setRequestedLotCode("LOT-TYPED");

        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(issueRepository.findByWorkOrder(eq(workOrder.getWorkOrderId()), any(), any()))
                .thenReturn(new PageImpl<>(List.of(issue)));
        when(issueLineRepository.findByIssueIssueIdIn(any())).thenReturn(List.of(issue.getLines().get(0)));
        when(userLookupService.findUsernames(any())).thenReturn(Map.of());
        when(inventoryLotLookupService.findLotCodes(any())).thenReturn(Map.of());

        var page = service.list(workOrder.getWorkOrderId(), null, PageRequest.of(0, 20));

        assertThat(page.content().get(0).lines().get(0).lotNumber()).isEqualTo("LOT-TYPED");
        verify(inventoryLotLookupService).findLotCodes(argThat(ids -> ids.isEmpty()));
    }

    /**
     * A line may carry both identifiers. Approval resolves the lot by id
     * ({@code InventoryMovementService.resolveExistingLot} only falls back to the code), so the queue
     * must show the code of the lot that will actually be consumed. Echoing the typed code instead
     * would show the approver one lot while a different one leaves the warehouse.
     */
    @Test
    void list_lineCarryingBothIdentifiers_showsTheLotApprovalWillActuallyConsume() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        WorkOrderComponentLine componentLine = workOrder.getComponentLines().get(0);
        UUID lotId = UUID.randomUUID();
        MaterialIssue issue = pendingIssueNamingLotById(workOrder, componentLine, lotId);
        issue.getLines().get(0).setRequestedLotCode("LOT-STALE-TYPED");

        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(issueRepository.findByWorkOrder(eq(workOrder.getWorkOrderId()), any(), any()))
                .thenReturn(new PageImpl<>(List.of(issue)));
        when(issueLineRepository.findByIssueIssueIdIn(any())).thenReturn(List.of(issue.getLines().get(0)));
        when(userLookupService.findUsernames(any())).thenReturn(Map.of());
        when(inventoryLotLookupService.findLotCodes(any())).thenReturn(Map.of(lotId, "LOT-REAL"));

        var page = service.list(workOrder.getWorkOrderId(), null, PageRequest.of(0, 20));

        assertThat(page.content().get(0).lines().get(0).lotNumber()).isEqualTo("LOT-REAL");
    }

    // ── G8b ────────────────────────────────────────────────────────────────────────────────────────

    private void stubForPost(WorkOrder workOrder, Warehouse warehouse, String key) {
        when(issueRepository.findWithLinesByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        lenient().when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(),
                workOrder.getPlant().getPlantId())).thenReturn(warehouse);
    }

    private MaterialIssueLineRequest line(WorkOrderComponentLine component, UUID reservationId, Warehouse warehouse,
                                          String quantity, MaterialIssueReasonCode reasonCode, String overrideReason,
                                          UUID sourceExecutionId) {
        return new MaterialIssueLineRequest(component.getComponentLineId(), reservationId, warehouse.getWarehouseId(),
                null, null, null, new BigDecimal(quantity), null, overrideReason, reasonCode, sourceExecutionId);
    }

    private void actingAs(UUID userId) {
        SecurityAuditorAware auditor = mock(SecurityAuditorAware.class);
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(userId));
        ReflectionTestUtils.setField(service, "auditorAware", auditor);
        authenticate();
    }

    @Test
    void post_withoutAWorkOrderVersion_isRefusedNamingTheField() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        stubForPost(workOrder, warehouse, "KEY-NOVERSION");

        assertThatThrownBy(() -> service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("x", List.of(
                line(component, null, warehouse, "2", null, null, null)), null, null), "KEY-NOVERSION"))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("expectedWorkOrderVersion"));
        verifyNoInteractions(movementService);
    }

    /** FE §1.4 step 6: two lines that each fit but together go over the BOM make the whole request pending. */
    @Test
    void post_twoLinesThatTogetherGoOverTheBom_turnTheWholeRequestPending() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        stubForPost(workOrder, warehouse, "KEY-TWO");
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        freeStockIsPlentiful();

        MaterialIssueResponse response = service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("x", List.of(
                line(component, null, warehouse, "6", MaterialIssueReasonCode.PROCESS_LOSS, "Loss in cutting", null),
                line(component, null, warehouse, "6", MaterialIssueReasonCode.PROCESS_LOSS, "Loss in cutting", null)),
                0L, null), "KEY-TWO");

        assertThat(response.status()).isEqualTo(MaterialIssueStatus.PENDING_APPROVAL.name());
        assertThat(response.lines()).allSatisfy(l -> assertThat(l.overIssue()).isTrue());
        verifyNoInteractions(movementService);
        assertThat(component.getIssuedQuantity()).isZero();
    }

    @Test
    void post_normalIssueOverTheBom_isRefusedInsteadOfTurningPending() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        stubForPost(workOrder, warehouse, "KEY-NORMALOVER");

        assertThatThrownBy(() -> service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("x", List.of(
                line(component, null, warehouse, "11", MaterialIssueReasonCode.OTHER, "Extra for safety", null)),
                0L, false), "KEY-NORMALOVER"))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    MultiErrorException error = (MultiErrorException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(BusinessErrorCode.OVER_BOM_APPROVAL_REQUIRED);
                    assertThat(error.getFieldErrors()).containsOnlyKeys("lines[0].quantity");
                });
        verify(issueRepository, never()).saveAndFlush(any());
    }

    @Test
    void post_supplementalLines_needFreeStockAndAReasonCodeAndAReason() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        stubForPost(workOrder, warehouse, "KEY-SUPP");

        assertThatThrownBy(() -> service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("x", List.of(
                line(component, UUID.randomUUID(), warehouse, "1", null, null, null)), 0L, true), "KEY-SUPP"))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys(
                        "lines[0].reservationId", "lines[0].reasonCode", "lines[0].overrideReason"));
        verifyNoInteractions(movementService);
    }

    /** D2: a normal line without a reservation comes from the component's own warehouse. */
    @Test
    void post_normalLineFromAnotherWarehouseThanTheComponents_isAScopeMismatch() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        Warehouse own = warehouse(UUID.randomUUID(), workOrder.getPlant());
        component.setWarehouse(own);
        Warehouse other = workOrder.getOutputWarehouse();
        stubForPost(workOrder, other, "KEY-WH");

        assertThatThrownBy(() -> service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("x", List.of(
                line(component, null, other, "1", null, null, null)), 0L, null), "KEY-WH"))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    MultiErrorException error = (MultiErrorException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH);
                    assertThat(error.getFieldErrors()).containsOnlyKeys("lines[0].warehouseId");
                });
        verifyNoInteractions(movementService);
    }

    @Test
    void post_sourceExecutionWithoutReworkReason_andAReasonTooShort_areFieldErrors() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        stubForPost(workOrder, warehouse, "KEY-RULES");

        assertThatThrownBy(() -> service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("x", List.of(
                line(component, null, warehouse, "1", MaterialIssueReasonCode.DAMAGE, "abc", UUID.randomUUID())),
                0L, null), "KEY-RULES"))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors())
                        .containsOnlyKeys("lines[0].reasonCode", "lines[0].overrideReason"));
    }

    /** FE Q10: a pending request does not hold stock, but its source must hold enough when it is made. */
    @Test
    void post_pendingRequestWhoseSourceHoldsTooLittle_isRefusedOnTheLine() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        stubForPost(workOrder, warehouse, "KEY-SHORT");
        when(inventoryAvailabilityService.getStockBalanceForIssue(any(), any(), any())).thenReturn(StockBalance.builder()
                .quantity(new BigDecimal("11")).reservedQuantity(BigDecimal.ZERO).build());

        assertThatThrownBy(() -> service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("x", List.of(
                line(component, null, warehouse, "12", MaterialIssueReasonCode.SETUP_LOSS, "Setup scrap", null)),
                0L, null), "KEY-SHORT"))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    MultiErrorException error = (MultiErrorException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK);
                    assertThat(error.getFieldErrors()).containsOnlyKeys("lines[0].quantity");
                });
        verify(issueRepository, never()).saveAndFlush(any());
    }

    /** Two lines drawing on the same source are added up: 6 and 6 each fit in 11, together they do not. */
    @Test
    void post_pendingRequestWhoseLinesTogetherDrainTheSource_isRefusedOnTheFirstLine() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        stubForPost(workOrder, warehouse, "KEY-SUM");
        when(inventoryAvailabilityService.getStockBalanceForIssue(any(), any(), any())).thenReturn(StockBalance.builder()
                .quantity(new BigDecimal("11")).reservedQuantity(BigDecimal.ZERO).build());

        assertThatThrownBy(() -> service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("x", List.of(
                line(component, null, warehouse, "6", MaterialIssueReasonCode.SETUP_LOSS, "Setup scrap", null),
                line(component, null, warehouse, "6", MaterialIssueReasonCode.SETUP_LOSS, "Setup scrap", null)),
                0L, null), "KEY-SUM"))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    MultiErrorException error = (MultiErrorException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK);
                    assertThat(error.getFieldErrors()).containsOnlyKeys("lines[0].quantity");
                });
        verify(issueRepository, never()).saveAndFlush(any());
    }

    @Test
    void approve_staleIssueVersion_isAConcurrentModificationBeforeAnyStockMoves() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        MaterialIssue pending = pendingIssue(workOrder, workOrder.getComponentLines().get(0), workOrder.getOutputWarehouse(),
                new BigDecimal("12"));
        when(issueRepository.findWithLinesByIssueId(pending.getIssueId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approve(workOrder.getWorkOrderId(), pending.getIssueId(),
                new MaterialIssueVersionedDecisionRequest(0L, 3L, null), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        verifyNoInteractions(movementService);
    }

    /**
     * R22: a retried approval with the same key answers with the current issue. The first approval moved both
     * versions on, so the replay has to be recognised before the version check — and before the "still
     * pending" check — or a network retry would be answered 409.
     */
    @Test
    void approve_replayedKeyAfterTheIssueWasPosted_returnsItWithoutPostingAgain() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.setVersion(4L);
        MaterialIssue posted = pendingIssue(workOrder, workOrder.getComponentLines().get(0), workOrder.getOutputWarehouse(),
                new BigDecimal("12"));
        posted.markPosted(Instant.now(), null);
        posted.setVersion(1L);
        MaterialIssueVersionedDecisionRequest request = new MaterialIssueVersionedDecisionRequest(3L, 0L, null);
        String hash = new IdempotencySupport(new ObjectMapper().findAndRegisterModules())
                .payloadHash(new WorkOrderIdempotency.Fingerprint(posted.getIssueId(), request));
        when(idempotencyRecordRepository.findByIdempotencyKeyAndCommand("APPROVE-1",
                com.erp.manufacturing.module.workorder.domain.WorkOrderCommand.ISSUE_APPROVE))
                .thenReturn(Optional.of(com.erp.manufacturing.module.workorder.domain.WorkOrderIdempotencyRecord.builder()
                        .idempotencyKey("APPROVE-1").resourceId(posted.getIssueId()).payloadHash(hash).build()));
        when(issueRepository.findWithLinesByIssueId(posted.getIssueId())).thenReturn(Optional.of(posted));

        var response = service.approve(workOrder.getWorkOrderId(), posted.getIssueId(), request, "APPROVE-1");

        assertThat(response.status()).isEqualTo("POSTED");
        verifyNoInteractions(movementService);
        verify(issueRepository, never()).saveAndFlush(any());
        verify(idempotencyRecordRepository, never()).saveAndFlush(any());
    }

    /** FE Q3: approving your own request needs OVERRIDE on top of APPROVE. */
    @Test
    void approve_ownRequestWithoutOverride_isDeniedBeforeAnyStockMoves() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        MaterialIssue pending = pendingIssue(workOrder, workOrder.getComponentLines().get(0), workOrder.getOutputWarehouse(),
                new BigDecimal("12"));
        UUID me = UUID.randomUUID();
        pending.setCreatedBy(me);
        actingAs(me);
        when(issueRepository.findWithLinesByIssueId(pending.getIssueId())).thenReturn(Optional.of(pending));
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_OVERRIDE"), eq(workOrder.getWorkOrderId())))
                .thenReturn(false);

        assertThatThrownBy(() -> service.approve(workOrder.getWorkOrderId(), pending.getIssueId(),
                new MaterialIssueVersionedDecisionRequest(0L, 0L, null), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_DENIED));
        verifyNoInteractions(movementService);
        assertThat(pending.getStatus()).isEqualTo(MaterialIssueStatus.PENDING_APPROVAL);
    }

    @Test
    void cancel_byTheRequester_withdrawsWithoutStockEffect_andMovesTheVersionOn() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        MaterialIssue pending = pendingIssue(workOrder, workOrder.getComponentLines().get(0), workOrder.getOutputWarehouse(),
                new BigDecimal("12"));
        UUID me = UUID.randomUUID();
        pending.setCreatedBy(me);
        actingAs(me);
        when(issueRepository.findWithLinesByIssueId(pending.getIssueId())).thenReturn(Optional.of(pending));
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialIssueResponse response = service.cancelByRequester(workOrder.getWorkOrderId(), pending.getIssueId(),
                new MaterialIssueVersionedDecisionRequest(0L, 0L, "  Not needed after all  "), null);

        assertThat(response.status()).isEqualTo(MaterialIssueStatus.CANCELLED.name());
        assertThat(response.cancellationReason()).isEqualTo("Not needed after all");
        assertThat(workOrder.getUpdatedAt()).isNotNull();
        verify(workOrderRepository).saveAndFlush(workOrder);
        verifyNoInteractions(movementService, wipTransactionService, costAccumulatorService);
    }

    @Test
    void cancel_bySomeoneElse_isDenied() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        MaterialIssue pending = pendingIssue(workOrder, workOrder.getComponentLines().get(0), workOrder.getOutputWarehouse(),
                new BigDecimal("12"));
        pending.setCreatedBy(UUID.randomUUID());
        actingAs(UUID.randomUUID());
        when(issueRepository.findWithLinesByIssueId(pending.getIssueId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.cancelByRequester(workOrder.getWorkOrderId(), pending.getIssueId(),
                new MaterialIssueVersionedDecisionRequest(0L, 0L, "Not needed after all"), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_DENIED));
        assertThat(pending.getStatus()).isEqualTo(MaterialIssueStatus.PENDING_APPROVAL);
    }

    /** FE Q33: a reason that is there but under 5 characters is a field error, not "missing". */
    @Test
    void reject_reasonUnderFiveCharacters_isAFieldError() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        MaterialIssue pending = pendingIssue(workOrder, workOrder.getComponentLines().get(0), workOrder.getOutputWarehouse(),
                new BigDecimal("12"));
        when(issueRepository.findWithLinesByIssueId(pending.getIssueId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.reject(workOrder.getWorkOrderId(), pending.getIssueId(),
                new MaterialIssueVersionedDecisionRequest(0L, 0L, "no"), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("reason"));
    }

    /** Decision context: requirement 10, gross 4, request 12 ⇒ extra 6; free stock 30 at the source. */
    @Test
    void getDetail_reportsTheDecisionContextOfAPendingRequest() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        component.setIssuedQuantity(new BigDecimal("4"));
        MaterialIssue pending = pendingIssue(workOrder, component, workOrder.getOutputWarehouse(), new BigDecimal("12"));
        when(issueRepository.findWithLinesByIssueId(pending.getIssueId())).thenReturn(Optional.of(pending));
        when(inventoryAvailabilityService.getStockBalanceForIssue(any(), any(), any())).thenReturn(StockBalance.builder()
                .quantity(new BigDecimal("35")).reservedQuantity(new BigDecimal("5")).build());

        var detail = service.getDetail(pending.getIssueId());

        var context = detail.decisionContext().get(0);
        assertThat(context.requiredQuantity()).isEqualByComparingTo("10");
        assertThat(context.grossIssuedQuantity()).isEqualByComparingTo("4");
        assertThat(context.batchRequestedQuantity()).isEqualByComparingTo("12");
        assertThat(context.extraRequestedQuantity()).isEqualByComparingTo("6");
        assertThat(context.currentAvailability()).isEqualByComparingTo("30");
        assertThat(detail.issue().version()).isEqualTo(0L);
    }

    /** Once posted there is nothing left to decide: the "extra over the BOM" figure is null, not a stale number. */
    @Test
    void getDetail_ofAPostedIssue_reportsNoExtraOverTheBom() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine component = workOrder.getComponentLines().get(0);
        component.setIssuedQuantity(new BigDecimal("16"));
        MaterialIssue posted = pendingIssue(workOrder, component, workOrder.getOutputWarehouse(), new BigDecimal("12"));
        posted.markPosted(Instant.now(), null);
        when(issueRepository.findWithLinesByIssueId(posted.getIssueId())).thenReturn(Optional.of(posted));
        freeStockIsPlentiful();

        var context = service.getDetail(posted.getIssueId()).decisionContext().get(0);

        assertThat(context.extraRequestedQuantity()).isNull();
        assertThat(context.batchRequestedQuantity()).isEqualByComparingTo("12");
    }

    private MaterialIssue pendingIssueNamingLotById(WorkOrder workOrder,
                                                    WorkOrderComponentLine componentLine,
                                                    UUID lotId) {
        MaterialIssue issue = MaterialIssue.builder()
                .issueId(UUID.randomUUID())
                .code("MI-PENDING")
                .workOrder(workOrder)
                .status(MaterialIssueStatus.PENDING_APPROVAL)
                .idempotencyKey(UUID.randomUUID().toString())
                .lines(new ArrayList<>())
                .build();
        issue.getLines().add(MaterialIssueLine.builder()
                .issueLineId(UUID.randomUUID())
                .issue(issue)
                .componentLine(componentLine)
                .item(componentLine.getComponentItem())
                .warehouse(workOrder.getOutputWarehouse())
                .quantity(new BigDecimal("5"))
                .requestedLotId(lotId)
                .overIssue(true)
                .overrideReason("Rework")
                .reasonCode(MaterialIssueReasonCode.REWORK)
                .build());
        return issue;
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
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
                        null)), 0L, null), "KEY-ISSUE");

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
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialIssueLineRequest lineRequest = new MaterialIssueLineRequest(
                line.getComponentLineId(), null, warehouse.getWarehouseId(), null, null,
                serialId, BigDecimal.ONE, null, null);
        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(lineRequest), 0L, null), "KEY-SERIAL");

        ArgumentCaptor<InventoryIssueCommand> commandCaptor = ArgumentCaptor.forClass(InventoryIssueCommand.class);
        verify(movementService).issue(commandCaptor.capture(), eq("KEY-SERIAL:L1"));
        assertThat(commandCaptor.getValue().serialId()).isEqualTo(serialId);

        ArgumentCaptor<MaterialIssue> issueCaptor = ArgumentCaptor.forClass(MaterialIssue.class);
        verify(issueRepository).saveAndFlush(issueCaptor.capture());
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
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialIssueLineRequest lineRequest = new MaterialIssueLineRequest(
                line.getComponentLineId(), reservation.getReservationId(), warehouse.getWarehouseId(), null, null,
                serialId, BigDecimal.ONE, null, null);
        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(lineRequest), 0L, null), "KEY-SERIAL-RES");

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
                issueLine(line, warehouse, BigDecimal.ONE, null)), 0L, null);

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-BLOCKED"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService);
        verify(issueRepository, never()).saveAndFlush(any());
    }

    /** P6: a CLOSED work order must not be able to issue material — it is locked completely. */
    @Test
    void post_onClosedWorkOrder_shouldThrow() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.CLOSED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(issueRepository.findWithLinesByIdempotencyKey("KEY-CLOSED")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        UUID workOrderId = workOrder.getWorkOrderId();
        MaterialIssuePostRequest request = new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, BigDecimal.ONE, null)), 0L, null);

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-CLOSED"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService);
        verify(issueRepository, never()).saveAndFlush(any());
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
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("10"), null)), 0L, null), "KEY-WITHIN");

        ArgumentCaptor<MaterialIssue> captor = ArgumentCaptor.forClass(MaterialIssue.class);
        verify(issueRepository).saveAndFlush(captor.capture());
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
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("4"), null)), 0L, null), "KEY-COST");

        verify(costAccumulatorService).accumulateMaterialCost(workOrder, line.getComponentItem(), new BigDecimal("4"));
    }

    @Test
    void postInternal_replayedIdempotencyKey_doesNotDoubleAccumulateMaterialCost() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        // payloadHash is null (B5c) — the builder never sets it — so ensureSamePayload() below
        // short-circuits without needing a matching hash; this test is only about accumulation.
        MaterialIssue existing = issue(workOrder, UUID.randomUUID());

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-REPLAY")).thenReturn(Optional.of(existing));

        service.postInternal(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(), 0L, null), "KEY-REPLAY");

        verifyNoInteractions(costAccumulatorService);
    }

    @Test
    void issue_exceedRemaining_operatorCreatesPendingRequestWithoutStockImpact() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        authenticate();

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-OVER")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        freeStockIsPlentiful(); // G8b (FE Q10): a pending request's source must hold the stock now

        MaterialIssuePostRequest request = new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("99"), "Needs rework")), 0L, null);
        UUID workOrderId = workOrder.getWorkOrderId();

        MaterialIssueResponse response = service.post(workOrderId, request, "KEY-OVER");

        assertThat(response.status()).isEqualTo(MaterialIssueStatus.PENDING_APPROVAL.name());
        verify(movementService, never()).issue(any(), anyString());
        assertThat(line.getIssuedQuantity()).isZero();
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

        MaterialIssuePostRequest request = new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("12"), "   ")), 0L, null);
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-BLANK"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.MISSING_REQUIRED_FIELD));

        verify(movementService, never()).issue(any(), anyString());
        verify(issueRepository, never()).saveAndFlush(any());
    }

    @Test
    void issue_exceedRemaining_withReason_shouldRemainPendingUntilManagerApproval() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        authenticate();

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-APPROVED")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        freeStockIsPlentiful(); // G8b (FE Q10): a pending request's source must hold the stock now

        MaterialIssueResponse response = service.post(workOrder.getWorkOrderId(), new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("12"), " Scrap rework ")), 0L, null), "KEY-APPROVED");

        ArgumentCaptor<MaterialIssue> captor = ArgumentCaptor.forClass(MaterialIssue.class);
        verify(issueRepository).saveAndFlush(captor.capture());
        MaterialIssueLine savedLine = captor.getValue().getLines().get(0);
        assertThat(savedLine.isOverIssue()).isTrue();
        assertThat(savedLine.getOverrideReason()).isEqualTo("Scrap rework");
        assertThat(savedLine.getReasonCode()).isEqualTo(MaterialIssueReasonCode.OTHER);
        assertThat(response.status()).isEqualTo(MaterialIssueStatus.PENDING_APPROVAL.name());
        assertThat(line.getIssuedQuantity()).isZero();
        verifyNoInteractions(movementService);
    }

    /**
     * A stranded PENDING_APPROVAL request is the failure mode the Over-BOM gate exists to prevent:
     * a lot-tracked component has no reservation to inherit a lot from, so a request without one
     * cannot ever be approved — the movement at approval time refuses it. Caught at request time the
     * operator can still say which lot they took; caught at approval time nobody can fix it.
     */
    @Test
    void issue_overBomForALotTrackedComponentWithoutALot_isRejectedAtRequestTime() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        line.getComponentItem().setLotTracked(true);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        authenticate();

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-NOLOT")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(
                warehouse.getWarehouseId(), workOrder.getPlant().getPlantId())).thenReturn(warehouse);

        MaterialIssuePostRequest request = new MaterialIssuePostRequest("Issue", List.of(
                issueLine(line, warehouse, new BigDecimal("25"), "Rework")), 0L, null);
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-NOLOT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.LOT_REQUIRED));

        verify(issueRepository, never()).saveAndFlush(any());
        verifyNoInteractions(movementService);
    }

    /** The same request is accepted once it names the lot — the guard must not block the happy path. */
    @Test
    void issue_overBomForALotTrackedComponentNamingTheLot_isAcceptedAsPending() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        line.getComponentItem().setLotTracked(true);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        UUID lotId = UUID.randomUUID();
        authenticate();

        when(issueRepository.findWithLinesByIdempotencyKey("KEY-WITHLOT")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(
                warehouse.getWarehouseId(), workOrder.getPlant().getPlantId())).thenReturn(warehouse);
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        freeStockIsPlentiful(); // G8b (FE Q10): a pending request's source must hold the stock now

        MaterialIssueResponse response = service.post(workOrder.getWorkOrderId(),
                new MaterialIssuePostRequest("Issue", List.of(new MaterialIssueLineRequest(
                        line.getComponentLineId(), null, warehouse.getWarehouseId(), lotId, null, null,
                        new BigDecimal("25"), null, "Rework", MaterialIssueReasonCode.REWORK, null)), 0L, null),
                "KEY-WITHLOT");

        assertThat(response.status()).isEqualTo(MaterialIssueStatus.PENDING_APPROVAL.name());
        ArgumentCaptor<MaterialIssue> captor = ArgumentCaptor.forClass(MaterialIssue.class);
        verify(issueRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLines().get(0).getRequestedLotId()).isEqualTo(lotId);
        verifyNoInteractions(movementService);
    }

    /**
     * DEC-09: posting stock is what approval buys. The request already existed and already carried
     * the quantity, so a mutation that moved the movement call back into {@code post} would keep
     * every earlier assertion green — this is the case that pins the stock impact to the approval.
     */
    @Test
    void approve_pendingRequest_postsTheMovementAndOnlyThenCountsTheIssuedQuantity() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        MaterialIssue pending = pendingIssue(workOrder, line, warehouse, new BigDecimal("12"));
        StockMovement movement = movement(line.getComponentItem(), warehouse);

        when(issueRepository.findWithLinesByIssueId(pending.getIssueId())).thenReturn(Optional.of(pending));
        when(movementService.issue(any(InventoryIssueCommand.class), eq(pending.getIdempotencyKey() + ":approve:L1")))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialIssueResponse response = service.approve(workOrder.getWorkOrderId(), pending.getIssueId(), new MaterialIssueVersionedDecisionRequest(0L, 0L, null), null);

        assertThat(response.status()).isEqualTo(MaterialIssueStatus.POSTED.name());
        assertThat(line.getIssuedQuantity()).isEqualByComparingTo("12");
        verify(costAccumulatorService).accumulateMaterialCost(workOrder, line.getComponentItem(), new BigDecimal("12"));
        verify(wipTransactionService).recordMaterialIssued(workOrder, new BigDecimal("12"), pending.getIssueId());
    }

    @Test
    void approve_anAlreadyDecidedRequest_throwsStateConflictWithoutPostingAnything() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        MaterialIssue posted = pendingIssue(workOrder, line, workOrder.getOutputWarehouse(), new BigDecimal("12"));
        posted.markPosted(Instant.now(), UUID.randomUUID());
        UUID workOrderId = workOrder.getWorkOrderId();
        UUID issueId = posted.getIssueId();

        when(issueRepository.findWithLinesByIssueId(issueId)).thenReturn(Optional.of(posted));

        assertThatThrownBy(() -> service.approve(workOrderId, issueId, new MaterialIssueVersionedDecisionRequest(0L, 0L, null), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService, wipTransactionService, costAccumulatorService);
        verify(issueRepository, never()).saveAndFlush(any());
    }

    /** DEC-09 requires a reason on reject; a silent rejection leaves the operator no explanation. */
    @Test
    void reject_withoutAReason_throwsBeforeRecordingTheDecision() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        MaterialIssue pending = pendingIssue(workOrder, line, workOrder.getOutputWarehouse(), new BigDecimal("12"));
        UUID workOrderId = workOrder.getWorkOrderId();
        UUID issueId = pending.getIssueId();
        MaterialIssueVersionedDecisionRequest blank = new MaterialIssueVersionedDecisionRequest(0L, 0L, "   ");

        when(issueRepository.findWithLinesByIssueId(issueId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.reject(workOrderId, issueId, blank, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.APPROVAL_REASON_REQUIRED));

        assertThat(pending.getStatus()).isEqualTo(MaterialIssueStatus.PENDING_APPROVAL);
        verify(issueRepository, never()).saveAndFlush(any());
        verifyNoInteractions(movementService);
    }

    @Test
    void reject_withAReason_recordsTheDecisionAndLeavesStockUntouched() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.RELEASED, new BigDecimal("10"));
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        MaterialIssue pending = pendingIssue(workOrder, line, workOrder.getOutputWarehouse(), new BigDecimal("12"));

        when(issueRepository.findWithLinesByIssueId(pending.getIssueId())).thenReturn(Optional.of(pending));
        when(issueRepository.saveAndFlush(any(MaterialIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialIssueResponse response = service.reject(workOrder.getWorkOrderId(), pending.getIssueId(),
                new MaterialIssueVersionedDecisionRequest(0L, 0L, "  Not justified  "), null);

        assertThat(response.status()).isEqualTo(MaterialIssueStatus.REJECTED.name());
        assertThat(pending.getRejectionReason()).isEqualTo("Not justified");
        assertThat(line.getIssuedQuantity()).isZero();
        verifyNoInteractions(movementService, wipTransactionService, costAccumulatorService);
    }

    /** G8b: the stock pre-check of a pending request finds more than enough free stock at every source. */
    private void freeStockIsPlentiful() {
        lenient().when(inventoryAvailabilityService.getStockBalanceForIssue(any(), any(), any()))
                .thenAnswer(invocation -> StockBalance.builder()
                        .quantity(new BigDecimal("1000")).reservedQuantity(BigDecimal.ZERO).build());
    }

    private MaterialIssue pendingIssue(WorkOrder workOrder,
                                       WorkOrderComponentLine componentLine,
                                       Warehouse warehouse,
                                       BigDecimal quantity) {
        MaterialIssue issue = MaterialIssue.builder()
                .issueId(UUID.randomUUID())
                .code("MI-TEST")
                .workOrder(workOrder)
                .status(MaterialIssueStatus.PENDING_APPROVAL)
                .idempotencyKey("KEY-PENDING")
                .lines(new ArrayList<>())
                .build();
        issue.setVersion(0L);
        issue.getLines().add(MaterialIssueLine.builder()
                .issueLineId(UUID.randomUUID())
                .issue(issue)
                .componentLine(componentLine)
                .item(componentLine.getComponentItem())
                .warehouse(warehouse)
                .quantity(quantity)
                .overIssue(true)
                .overrideReason("Rework")
                .reasonCode(MaterialIssueReasonCode.REWORK)
                .build());
        return issue;
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
                overrideReason,
                MaterialIssueReasonCode.OTHER,
                null);
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
