package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.SerialNumber;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.MaterialIssue;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueLine;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.MaterialReturn;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnLineRequest;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnPostRequest;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipMaterialLineResponse;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionConsumptionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.erp.manufacturing.module.workorder.service.execution.ProductionExecutionServiceTest.keyed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialReturnServiceTest {

    @Mock MaterialReturnRepository returnRepository;
    @Mock MaterialReturnLineRepository returnLineRepository;
    @Mock MaterialIssueLineRepository issueLineRepository;
    @Mock ProductionExecutionConsumptionRepository consumptionRepository;
    @Mock InventoryMovementService movementService;
    @Mock WipTransactionService wipTransactionService;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;
    @Mock UserLookupService userLookupService;
    @Mock SecurityAuditorAware auditorAware;

    private MaterialReturnService service;
    private WorkOrder workOrder;
    private Warehouse warehouse;
    private Item raw;

    @BeforeEach
    void setUp() {
        MaterialSourceLedger ledger = new MaterialSourceLedger(issueLineRepository, consumptionRepository, returnLineRepository);
        service = new MaterialReturnService(returnRepository, returnLineRepository, issueLineRepository, ledger,
                movementService, wipTransactionService,
                new WorkOrderExecutionSupport(workOrderRepository, organizationLookupService, inventoryAvailabilityService),
                new IdempotencySupport(new ObjectMapper().findAndRegisterModules()), new TraceIdProvider(),
                userLookupService, auditorAware);
        Company company = Company.builder().companyId(UUID.randomUUID()).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P1").name("Plant 1")
                .status(OrganizationStatus.ACTIVE).build();
        warehouse = Warehouse.builder().warehouseId(UUID.randomUUID()).plant(plant).code("RAW").name("Raw")
                .status(OrganizationStatus.ACTIVE).build();
        raw = Item.builder().itemId(UUID.randomUUID()).company(company).code("RM-1").name("Steel").type(ItemType.RAW_MATERIAL)
                .unit("KG").status(ItemStatus.ACTIVE).lotTracked(true).build();
        Item product = Item.builder().itemId(UUID.randomUUID()).company(company).code("FG-1").name("Frame")
                .type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).build();
        workOrder = WorkOrder.builder().workOrderId(UUID.randomUUID()).company(company).plant(plant).workOrderNo("WO-1")
                .productItem(product).plannedQuantity(BigDecimal.TEN).completedQuantity(BigDecimal.ZERO)
                .actualGoodQuantity(BigDecimal.ZERO).actualScrapQuantity(BigDecimal.ZERO)
                .actualReworkQuantity(BigDecimal.ZERO).status(WorkOrderStatus.IN_PROGRESS)
                .componentLines(new ArrayList<>()).operations(new ArrayList<>()).build();
        workOrder.setVersion(4L);
        lenient().when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
    }

    /**
     * F1 of the FE handoff: 20 kg issued from lot L1, 16 used ⇒ 4 unused; two entries of 1 for the same line are one
     * line of 2. The stock goes back to the issue line's own warehouse and lot as a RECEIVE referencing the return; the
     * gross issued quantity of the component does not move (FE Q11); the work order version moves once.
     */
    @Test
    void post_returnsUnusedMaterialToTheIssueLinesOwnSourceAndMovesTheVersionOnce() {
        InventoryLot lot = lot(LotStatus.AVAILABLE, null);
        MaterialIssueLine line = issueLine("20", lot);
        MaterialIssueLine plain = issueLine("1", null);
        stubPosted(line, plain);
        when(consumptionRepository.sumByIssueLineIds(anyCollection())).thenReturn(List.of(keyed(line.getIssueLineId(), "16")));
        List<MaterialReturn> saved = stubSaves();
        when(movementService.receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.AVAILABLE)))
                .thenAnswer(invocation -> new InventoryMovementResult(
                        StockMovement.builder().movementId(UUID.randomUUID()).build(), true));
        when(returnLineRepository.findWithSourceByMaterialReturnIds(anyCollection()))
                .thenAnswer(invocation -> saved.get(0).getLines());

        MaterialReturnResponse response = service.post(workOrder.getWorkOrderId(), request("Unused after the run",
                entry(line, "1"), entry(plain, "1"), entry(line, "1")), "KEY-R");

        ArgumentCaptor<InventoryReceiveCommand> commands = ArgumentCaptor.forClass(InventoryReceiveCommand.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(movementService, org.mockito.Mockito.times(2)).receive(commands.capture(), keys.capture(), eq(LotStatus.AVAILABLE));
        InventoryReceiveCommand first = commands.getAllValues().get(0);
        assertThat(first.itemId()).isEqualTo(raw.getItemId());
        assertThat(first.warehouseId()).isEqualTo(warehouse.getWarehouseId());
        assertThat(first.lotId()).isEqualTo(lot.getLotId());
        assertThat(first.quantity()).isEqualByComparingTo("2");
        assertThat(first.referenceType()).isEqualTo("MATERIAL_RETURN");
        assertThat(first.referenceId()).isEqualTo(saved.get(0).getMaterialReturnId().toString());
        assertThat(commands.getAllValues().get(1).lotId()).isNull();
        assertThat(commands.getAllValues().get(1).quantity()).isEqualByComparingTo("1");
        assertThat(keys.getAllValues()).containsExactly("MR:KEY-R:L1", "MR:KEY-R:L2");

        verify(wipTransactionService).recordMaterialReturned(eq(workOrder), eq(new BigDecimal("3")),
                eq(saved.get(0).getMaterialReturnId()));
        verify(workOrderRepository).saveAndFlush(workOrder);
        assertThat(workOrder.getUpdatedAt()).isNotNull();
        assertThat(line.getComponentLine().getIssuedQuantity()).isEqualByComparingTo("21");
        assertThat(saved.get(0).getReason()).isEqualTo("Unused after the run");
        assertThat(response.lines()).hasSize(2);
        assertThat(response.lines().get(0).sourceIssueLineId()).isEqualTo(line.getIssueLineId());
        assertThat(response.lines().get(0).quantity()).isEqualByComparingTo("2");
        assertThat(response.lines().get(0).lotNumber()).isEqualTo("L1");
    }

    /** FE Q23: a completed work order can still give back what it did not use — exactly the unused part is allowed. */
    @Test
    void post_onACompletedWorkOrder_acceptsExactlyTheUnusedPart() {
        workOrder.setStatus(WorkOrderStatus.COMPLETED);
        MaterialIssueLine line = issueLine("20", null);
        stubPosted(line);
        when(consumptionRepository.sumByIssueLineIds(anyCollection())).thenReturn(List.of(keyed(line.getIssueLineId(), "16")));
        when(returnLineRepository.sumBySourceIssueLineIds(anyCollection())).thenReturn(List.of(keyed(line.getIssueLineId(), "1")));
        stubSaves();
        when(movementService.receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.AVAILABLE)))
                .thenReturn(new InventoryMovementResult(StockMovement.builder().movementId(UUID.randomUUID()).build(), true));

        service.post(workOrder.getWorkOrderId(), request("Unused after the run", entry(line, "3")), "KEY-EXACT");

        verify(movementService).receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.AVAILABLE));
    }

    /** 20 issued, 16 used, 1 returned ⇒ 3 unused; 2 + 2 for the same line is 4 — refused on the first entry, nothing posted. */
    @Test
    void post_overTheUnusedPart_isPlannedQuantityExceededOnTheFirstEntryOfTheLine() {
        MaterialIssueLine line = issueLine("20", null);
        stubPosted(line);
        when(consumptionRepository.sumByIssueLineIds(anyCollection())).thenReturn(List.of(keyed(line.getIssueLineId(), "16")));
        when(returnLineRepository.sumBySourceIssueLineIds(anyCollection())).thenReturn(List.of(keyed(line.getIssueLineId(), "1")));

        assertFieldError(() -> service.post(workOrder.getWorkOrderId(),
                        request("Unused after the run", entry(line, "2"), entry(line, "2")), "KEY-OVER"),
                BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, "lines[0].quantity", "only 3 of this issue line is unused");
        verifyNoInteractions(movementService, wipTransactionService);
        verify(returnRepository, never()).saveAndFlush(any());
    }

    @Test
    void post_lineThatIsNotAPostedIssueLineOfTheWorkOrder_isAScopeMismatch() {
        stubPosted(issueLine("20", null));

        assertFieldError(() -> service.post(workOrder.getWorkOrderId(), request("Unused after the run",
                        new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.ONE)), "KEY-SCOPE"),
                BusinessErrorCode.RESOURCE_SCOPE_MISMATCH, "lines[0].sourceIssueLineId", null);
        verifyNoInteractions(movementService);
    }

    /** FE A31: the original lot is on hold now — the return is refused instead of slipping past QC as a generic receipt. */
    @Test
    void post_toALotNowOnHold_isLotNotEligible() {
        MaterialIssueLine line = issueLine("20", lot(LotStatus.HOLD, null));
        stubPosted(line);

        assertFieldError(() -> service.post(workOrder.getWorkOrderId(), request("Unused after the run", entry(line, "1")),
                "KEY-HOLD"), BusinessErrorCode.LOT_NOT_ELIGIBLE, "lines[0].sourceIssueLineId", null);
        verifyNoInteractions(movementService);
    }

    @Test
    void post_toAnExpiredLot_isLotNotEligible() {
        MaterialIssueLine line = issueLine("20", lot(LotStatus.AVAILABLE, Instant.now().minus(1, ChronoUnit.DAYS)));
        stubPosted(line);

        assertFieldError(() -> service.post(workOrder.getWorkOrderId(), request("Unused after the run", entry(line, "1")),
                "KEY-EXP"), BusinessErrorCode.LOT_NOT_ELIGIBLE, "lines[0].sourceIssueLineId", null);
        verifyNoInteractions(movementService);
    }

    @Test
    void post_toAnInactiveWarehouse_isResourceInactive() {
        MaterialIssueLine line = issueLine("20", null);
        warehouse.setStatus(OrganizationStatus.INACTIVE);
        stubPosted(line);

        assertFieldError(() -> service.post(workOrder.getWorkOrderId(), request("Unused after the run", entry(line, "1")),
                "KEY-WH"), BusinessErrorCode.RESOURCE_INACTIVE, "lines[0].sourceIssueLineId", null);
        verifyNoInteractions(movementService);
    }

    @Test
    void post_ofASerialTrackedLine_isNotAllowed() {
        MaterialIssueLine line = issueLine("1", null);
        line.setSerial(SerialNumber.builder().serialId(UUID.randomUUID()).item(raw).serialCode("S-1").build());
        stubPosted(line);

        assertFieldError(() -> service.post(workOrder.getWorkOrderId(), request("Unused after the run", entry(line, "1")),
                "KEY-SER"), BusinessErrorCode.OPERATION_NOT_ALLOWED, "lines[0].sourceIssueLineId", null);
        verifyNoInteractions(movementService);
    }

    @Test
    void post_onAReleasedWorkOrder_isAStateConflict() {
        workOrder.setStatus(WorkOrderStatus.RELEASED);
        stubWorkOrder();

        assertCode(() -> service.post(workOrder.getWorkOrderId(), request("Unused after the run",
                new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.ONE)), "KEY-REL"), BusinessErrorCode.STATE_CONFLICT);
        verifyNoInteractions(movementService, issueLineRepository);
    }

    /** FE Q22: a work order reported before consumption was recorded has no known unused part — nothing is returnable. */
    @Test
    void post_onAWorkOrderWithoutConsumptionEvidence_isProductionInputUnknown() {
        workOrder.setOperationTracking(false);
        stubWorkOrder();

        assertCode(() -> service.post(workOrder.getWorkOrderId(), request("Unused after the run",
                new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.ONE)), "KEY-LEG"),
                BusinessErrorCode.PRODUCTION_INPUT_UNKNOWN);
        verifyNoInteractions(movementService, issueLineRepository);
    }

    @Test
    void post_withoutAReason_isApprovalReasonRequired_andATooShortOneIsInvalid() {
        stubWorkOrder();
        assertFieldError(() -> service.post(workOrder.getWorkOrderId(), request(null,
                        new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.ONE)), "KEY-NR"),
                ValidationErrorCode.APPROVAL_REASON_REQUIRED, "reason", null);
        assertFieldError(() -> service.post(workOrder.getWorkOrderId(), request("abc",
                        new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.ONE)), "KEY-SR"),
                ValidationErrorCode.INVALID_INPUT, "reason", null);
        verifyNoInteractions(movementService);
    }

    @Test
    void post_withAStaleVersion_isConcurrentModificationBeforeAnyWrite() {
        stubWorkOrder();

        assertCode(() -> service.post(workOrder.getWorkOrderId(), new MaterialReturnPostRequest(3L, "Unused after the run",
                List.of(new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.ONE))), "KEY-STALE"),
                BusinessErrorCode.CONCURRENT_MODIFICATION);
        verifyNoInteractions(movementService, issueLineRepository);
    }

    /** R9: the same key and body replays the first return — no stock moves and the work order is not even loaded. */
    @Test
    void post_replayedKey_returnsTheFirstReturnWithoutPostingStock() {
        MaterialReturnPostRequest request = request("Unused after the run",
                new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.ONE));
        MaterialReturn existing = existingReturn(request);
        when(returnRepository.findWithWorkOrderByIdempotencyKey("KEY-REPLAY")).thenReturn(Optional.of(existing));

        MaterialReturnResponse response = service.post(workOrder.getWorkOrderId(), request, "KEY-REPLAY");

        assertThat(response.materialReturnId()).isEqualTo(existing.getMaterialReturnId());
        verifyNoInteractions(movementService, workOrderRepository, issueLineRepository);
    }

    @Test
    void post_replayedKeyWithAnotherBody_isAnIdempotencyConflict() {
        MaterialReturn existing = existingReturn(request("Unused after the run",
                new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.ONE)));
        when(returnRepository.findWithWorkOrderByIdempotencyKey("KEY-REPLAY")).thenReturn(Optional.of(existing));

        assertCode(() -> service.post(workOrder.getWorkOrderId(), request("Unused after the run",
                new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.TEN)), "KEY-REPLAY"),
                BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        verifyNoInteractions(movementService);
    }

    /** Lines with nothing unused are left out; a source that cannot take material back shows 0 returnable and why. */
    @Test
    void candidates_listLinesWithUnusedMaterialAndTheirReturnableCap() {
        MaterialIssueLine usedUp = issueLine("5", null);
        MaterialIssueLine onHold = issueLine("8", lot(LotStatus.HOLD, null));
        MaterialIssueLine open = issueLine("6", null);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(issueLineRepository.findPostedByWorkOrder(workOrder.getWorkOrderId())).thenReturn(List.of(usedUp, onHold, open));
        when(consumptionRepository.sumByIssueLineIds(anyCollection()))
                .thenReturn(List.of(keyed(usedUp.getIssueLineId(), "5"), keyed(open.getIssueLineId(), "2")));
        when(returnLineRepository.sumBySourceIssueLineIds(anyCollection())).thenReturn(List.of(keyed(open.getIssueLineId(), "1")));

        PageResult<WipMaterialLineResponse> page = service.candidates(workOrder.getWorkOrderId(), PageRequest.of(0, 20));

        assertThat(page.content()).extracting(WipMaterialLineResponse::issueLineId)
                .containsExactly(onHold.getIssueLineId(), open.getIssueLineId());
        WipMaterialLineResponse held = page.content().get(0);
        assertThat(held.unusedQuantity()).isEqualByComparingTo("8");
        assertThat(held.returnableQuantity()).isEqualByComparingTo("0");
        assertThat(held.eligible()).isFalse();
        assertThat(held.ineligibleReason()).isEqualTo("LOT_HOLD");
        WipMaterialLineResponse free = page.content().get(1);
        assertThat(free.consumedQuantity()).isEqualByComparingTo("2");
        assertThat(free.returnedQuantity()).isEqualByComparingTo("1");
        assertThat(free.unusedQuantity()).isEqualByComparingTo("3");
        assertThat(free.returnableQuantity()).isEqualByComparingTo("3");
        assertThat(page.totalElements()).isEqualTo(2);
    }

    /** FE Q22: without consumption evidence the unused part is unknown — null, not 0 and not "everything issued". */
    @Test
    void candidates_onAWorkOrderWithoutConsumptionEvidence_reportNullQuantities() {
        workOrder.setOperationTracking(false);
        MaterialIssueLine line = issueLine("21", null);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(issueLineRepository.findPostedByWorkOrder(workOrder.getWorkOrderId())).thenReturn(List.of(line));

        WipMaterialLineResponse row = service.candidates(workOrder.getWorkOrderId(), PageRequest.of(0, 20)).content().get(0);

        assertThat(row.consumptionKnown()).isFalse();
        assertThat(row.consumedQuantity()).isNull();
        assertThat(row.unusedQuantity()).isNull();
        assertThat(row.returnableQuantity()).isNull();
        assertThat(row.returnedQuantity()).isEqualByComparingTo("0");
    }

    private void stubWorkOrder() {
        when(returnRepository.findWithWorkOrderByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
    }

    private void stubPosted(MaterialIssueLine... lines) {
        stubWorkOrder();
        when(issueLineRepository.findPostedByWorkOrder(workOrder.getWorkOrderId())).thenReturn(List.of(lines));
    }

    /** The return gets its id on the first save, like JPA would; the list collects what was saved. */
    private List<MaterialReturn> stubSaves() {
        List<MaterialReturn> saved = new ArrayList<>();
        when(returnRepository.saveAndFlush(any(MaterialReturn.class))).thenAnswer(invocation -> {
            MaterialReturn materialReturn = invocation.getArgument(0);
            if (materialReturn.getMaterialReturnId() == null) {
                materialReturn.setMaterialReturnId(UUID.randomUUID());
                materialReturn.assignCode();
                saved.add(materialReturn);
            }
            return materialReturn;
        });
        return saved;
    }

    private MaterialReturn existingReturn(MaterialReturnPostRequest request) {
        record ReturnCommand(UUID workOrderId, MaterialReturnPostRequest request) {
        }
        MaterialReturn existing = MaterialReturn.builder()
                .materialReturnId(UUID.randomUUID())
                .code("MR-1")
                .workOrder(workOrder)
                .reason("Unused after the run")
                .postedAt(Instant.now())
                .idempotencyKey("KEY-REPLAY")
                .payloadHash(new IdempotencySupport(new ObjectMapper().findAndRegisterModules())
                        .payloadHash(new ReturnCommand(workOrder.getWorkOrderId(), request)))
                .build();
        lenient().when(returnLineRepository.findWithSourceByMaterialReturnIds(anyCollection())).thenReturn(List.of());
        return existing;
    }

    private MaterialIssueLine issueLine(String quantity, InventoryLot lot) {
        WorkOrderComponentLine component = WorkOrderComponentLine.builder()
                .componentLineId(UUID.randomUUID())
                .workOrder(workOrder)
                .componentItem(raw)
                .lineNo(1)
                .quantityPer(new BigDecimal("2"))
                .requiredQuantity(new BigDecimal("20"))
                .issuedQuantity(new BigDecimal("21"))
                .build();
        MaterialIssue issue = MaterialIssue.builder()
                .issueId(UUID.randomUUID())
                .code("MI-1")
                .workOrder(workOrder)
                .status(MaterialIssueStatus.POSTED)
                .postedAt(Instant.now())
                .build();
        return MaterialIssueLine.builder()
                .issueLineId(UUID.randomUUID())
                .issue(issue)
                .componentLine(component)
                .item(raw)
                .warehouse(warehouse)
                .lot(lot)
                .quantity(new BigDecimal(quantity))
                .build();
    }

    private InventoryLot lot(LotStatus status, Instant expiresAt) {
        return InventoryLot.builder().lotId(UUID.randomUUID()).item(raw).lotCode("L1").status(status).expiresAt(expiresAt).build();
    }

    private MaterialReturnPostRequest request(String reason, MaterialReturnLineRequest... lines) {
        return new MaterialReturnPostRequest(4L, reason, List.of(lines));
    }

    private static MaterialReturnLineRequest entry(MaterialIssueLine line, String quantity) {
        return new MaterialReturnLineRequest(line.getIssueLineId(), new BigDecimal(quantity));
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }

    private static void assertFieldError(Runnable call, ErrorCode code, String field, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(code);
                    Map<String, String> errors = ((MultiErrorException) ex).getFieldErrors();
                    assertThat(errors).containsKey(field);
                    if (message != null) {
                        assertThat(errors.get(field)).isEqualTo(message);
                    }
                });
    }
}
