package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.InventoryAdjustCommand;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.LotStatusChangeCommand;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptPostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptQcDispositionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptRejectRequest;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.OpenReceiptQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
import com.erp.manufacturing.module.workorder.repository.QualityDispositionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderDemandAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductionReceiptService tests")
class ProductionReceiptServiceTest {

    @Mock ProductionReceiptRepository receiptRepository;
    @Mock ProductionReceiptLineRepository receiptLineRepository;
    @Mock ProductionExecutionRepository executionRepository;
    @Mock UserLookupService userLookupService;
    @Mock QualityDispositionRepository dispositionRepository;
    @Mock InventoryMovementService movementService;
    @Mock WipTransactionService wipTransactionService;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;
    @Mock ItemLookupService itemLookupService;
    @Mock SecurityAuditorAware auditorAware;
    @Mock WorkOrderDemandAllocationService allocationService;

    ProductionReceiptService service;

    @BeforeEach
    void setUp() {
        WorkOrderExecutionSupport support = new WorkOrderExecutionSupport(
                workOrderRepository, organizationLookupService, inventoryAvailabilityService);
        service = new ProductionReceiptService(
                receiptRepository,
                workOrderRepository,
                receiptLineRepository,
                executionRepository,
                dispositionRepository,
                movementService,
                wipTransactionService,
                itemLookupService,
                userLookupService,
                auditorAware,
                allocationService,
                support,
                new ManufacturingExecutionMapper(),
                new IdempotencySupport(new ObjectMapper()),
                new TraceIdProvider());
    }

    /**
     * The receipt candidates screen (spec §6.2, invariant B76). What the <em>filtering</em> does is
     * JPQL and lives in {@code WorkOrderRepositoryIT} (rule R7); what is asserted here is the Java
     * half — that {@code COMPLETED} is in the status set the query is asked for.
     *
     * <p>🔴 That status is the whole point of B76 and the thing debt #25 (D11) was: the shop floor
     * being done does not mean the goods reached the racks, so the last receipt of every work order
     * is made against a {@code COMPLETED} one.
     */
    @Test
    void listCandidates_asksForCompletedWorkOrdersToo_unlikeTheExecutionCandidates() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.COMPLETED, new BigDecimal("10"));
        UUID plantId = workOrder.getPlant().getPlantId();
        when(workOrderRepository.findReceiptCandidates(eq(plantId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(workOrder)));
        when(receiptRepository.sumOpenQuantityByWorkOrderIds(any())).thenReturn(List.of());

        var page = service.listCandidates(plantId, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).workOrderId()).isEqualTo(workOrder.getWorkOrderId());
        assertThat(page.content().get(0).uom()).isEqualTo("EA");
        assertThat(page.content().get(0).outputTrackingMethod()).isEqualTo("NON_TRACKED");

        ArgumentCaptor<Collection<WorkOrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(workOrderRepository).findReceiptCandidates(eq(plantId), statuses.capture(), any());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED);
    }

    /**
     * The number on the row must be the one {@code postNew} will actually accept — i.e. already net
     * of receipts still open (B16). {@code availableToReceipt()} alone does not deduct them, so a
     * mapper working off the entity by itself would advertise 10 where only 4 can be claimed.
     *
     * <p>The deduction is fetched once for the whole page (rule C15), never per row.
     */
    @Test
    void listCandidates_reportsTheCeilingNetOfOpenReceipts_inOneBatchQuery() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        UUID plantId = workOrder.getPlant().getPlantId();
        when(workOrderRepository.findReceiptCandidates(eq(plantId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(workOrder)));
        when(receiptRepository.sumOpenQuantityByWorkOrderIds(any()))
                .thenReturn(List.of(openQuantity(workOrder.getWorkOrderId(), new BigDecimal("6"))));

        var row = service.listCandidates(plantId, PageRequest.of(0, 20)).content().get(0);

        assertThat(row.actualGoodQuantity()).isEqualByComparingTo("10");
        assertThat(row.receiptedQuantity()).isEqualByComparingTo("0");
        assertThat(row.availableToReceipt()).isEqualByComparingTo("4");
        verify(receiptRepository, times(1)).sumOpenQuantityByWorkOrderIds(any());
    }

    private OpenReceiptQuantityProjection openQuantity(UUID workOrderId, BigDecimal quantity) {
        return new OpenReceiptQuantityProjection() {
            @Override public UUID getWorkOrderId() { return workOrderId; }
            @Override public BigDecimal getOpenQuantity() { return quantity; }
        };
    }

    @Test
    void post_shouldCreateDraftReceipt_withoutInventoryMovement() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-RECEIPT")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(BigDecimal.ZERO);
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), receiptRequest(warehouse, new BigDecimal("10")), "KEY-RECEIPT");

        ArgumentCaptor<ProductionReceipt> captor = ArgumentCaptor.forClass(ProductionReceipt.class);
        verify(receiptRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProductionReceiptStatus.DRAFT);
        assertThat(captor.getValue().getSubmittedAt()).isNull();
        assertThat(captor.getValue().getLines().get(0).getStockMovement()).isNull();
        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("0");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verifyNoInteractions(movementService, wipTransactionService);
    }

    @Test
    void post_lotTrackedOutputWithoutLot_shouldThrowLotRequired() {
        // Spec §6.3: a lot-tracked output can never leave HOLD without a lot, so refuse at create
        // time instead of failing at approval.
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"), true);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-NOLOT")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(BigDecimal.ZERO);

        UUID workOrderId = workOrder.getWorkOrderId();
        ProductionReceiptPostRequest request = receiptRequest(warehouse, new BigDecimal("10"));

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-NOLOT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.LOT_REQUIRED));

        verify(receiptRepository, never()).save(any());
        verifyNoInteractions(movementService, wipTransactionService);
    }

    // ── Serial-tracked output (P5) ──────────────────────────────────────────

    @Test
    void post_serialTrackedOutputWithoutSerial_shouldThrowSerialRequired() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.getProductItem().setSerialTracked(true);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-NOSERIAL")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(BigDecimal.ZERO);

        UUID workOrderId = workOrder.getWorkOrderId();
        ProductionReceiptPostRequest request = receiptRequest(warehouse, BigDecimal.ONE);

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-NOSERIAL"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.SERIAL_REQUIRED));

        verify(receiptRepository, never()).save(any());
        verifyNoInteractions(movementService, wipTransactionService);
    }

    @Test
    void post_serialTrackedOutputWithQuantityOtherThanOne_shouldThrowOperationNotAllowed() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.getProductItem().setSerialTracked(true);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-SN-QTY")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(BigDecimal.ZERO);

        UUID workOrderId = workOrder.getWorkOrderId();
        ProductionReceiptPostRequest request = serialReceiptRequest(warehouse, new BigDecimal("2"), "SN-1");

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-SN-QTY"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(receiptRepository, never()).save(any());
        verifyNoInteractions(movementService, wipTransactionService);
    }

    @Test
    void post_serialTrackedOutputWithSerial_setsRequestedSerialCodeOnTheLine() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        workOrder.getProductItem().setSerialTracked(true);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-SN-OK")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(BigDecimal.ZERO);
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), serialReceiptRequest(warehouse, BigDecimal.ONE, "SN-1"), "KEY-SN-OK");

        ArgumentCaptor<ProductionReceipt> captor = ArgumentCaptor.forClass(ProductionReceipt.class);
        verify(receiptRepository).save(captor.capture());
        assertThat(captor.getValue().getLines().get(0).getRequestedSerialCode()).isEqualTo("SN-1");
        verifyNoInteractions(movementService);
    }

    @Test
    void approve_serialTrackedOutput_createsSerialAndSetsItOnTheLine() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, BigDecimal.ONE);
        workOrder.getProductItem().setSerialTracked(true);
        Warehouse warehouse = workOrder.getOutputWarehouse();
        ProductionReceipt receipt = receiptWithSerial(workOrder, warehouse, BigDecimal.ONE,
                ProductionReceiptStatus.PENDING_APPROVAL, "SN-2");
        SerialNumber serial = SerialNumber.builder()
                .serialId(UUID.randomUUID())
                .item(workOrder.getProductItem())
                .serialCode("SN-2")
                .status(SerialStatus.AVAILABLE)
                .build();
        StockMovement movement = StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(workOrder.getProductItem())
                .warehouse(warehouse)
                .serial(serial)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("KEY")
                .createdAt(Instant.now())
                .build();

        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(movementService.receive(any(InventoryReceiveCommand.class),
                eq(receipt.getIdempotencyKey() + ":approve:L1"), eq(LotStatus.HOLD)))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(workOrder.getWorkOrderId(), receipt.getReceiptId());

        ArgumentCaptor<InventoryReceiveCommand> commandCaptor = ArgumentCaptor.forClass(InventoryReceiveCommand.class);
        verify(movementService).receive(commandCaptor.capture(), anyString(), eq(LotStatus.HOLD));
        assertThat(commandCaptor.getValue().serialCode()).isEqualTo("SN-2");
        assertThat(receipt.getLines().get(0).getSerial()).isSameAs(serial);
        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("1");
    }

    // ── QC disposition on serial-tracked output (P5) ────────────────────────

    @Test
    void qcDisposition_available_onSerialTrackedOutput_fulfilsAndMarksSerialAvailable() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, BigDecimal.ONE);
        workOrder.getProductItem().setSerialTracked(true);
        SerialNumber serial = SerialNumber.builder()
                .serialId(UUID.randomUUID())
                .item(workOrder.getProductItem())
                .serialCode("SN-3")
                .status(SerialStatus.AVAILABLE)
                .build();
        ProductionReceipt receipt = receiptWithApprovedSerial(workOrder, workOrder.getOutputWarehouse(),
                BigDecimal.ONE, serial);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.qcDisposition(workOrder.getWorkOrderId(), receipt.getReceiptId(),
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, "Passed"));

        assertThat(serial.getStatus()).isEqualTo(SerialStatus.AVAILABLE);
        verify(allocationService).fulfill(same(workOrder), argThat(quantity ->
                quantity.compareTo(BigDecimal.ONE) == 0));
        verify(movementService, never()).adjust(any(), anyString());
        verify(movementService, never()).changeLotStatus(any(), anyString());
        verifyNoInteractions(dispositionRepository);
    }

    @Test
    void qcDisposition_rejected_onSerialTrackedOutput_withdrawsAndMarksSerialRejected() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, BigDecimal.ONE);
        workOrder.getProductItem().setSerialTracked(true);
        SerialNumber serial = SerialNumber.builder()
                .serialId(UUID.randomUUID())
                .item(workOrder.getProductItem())
                .serialCode("SN-4")
                .status(SerialStatus.AVAILABLE)
                .build();
        Warehouse warehouse = workOrder.getOutputWarehouse();
        ProductionReceipt receipt = receiptWithApprovedSerial(workOrder, warehouse, BigDecimal.ONE, serial);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.qcDisposition(workOrder.getWorkOrderId(), receipt.getReceiptId(),
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.REJECTED, "Damaged"));

        assertThat(serial.getStatus()).isEqualTo(SerialStatus.REJECTED);
        ArgumentCaptor<InventoryAdjustCommand> commandCaptor = ArgumentCaptor.forClass(InventoryAdjustCommand.class);
        verify(movementService).adjust(commandCaptor.capture(),
                eq(receipt.getIdempotencyKey() + ":qc-reject:L1"));
        assertThat(commandCaptor.getValue().serialId()).isEqualTo(serial.getSerialId());
        assertThat(commandCaptor.getValue().quantityDelta()).isEqualByComparingTo("-1");
        verifyNoInteractions(allocationService, dispositionRepository);
    }

    @Test
    void submit_draftReceipt_movesToPendingApproval_withoutInventoryImpact() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("10"), ProductionReceiptStatus.DRAFT, null);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(workOrder.getWorkOrderId(), receipt.getReceiptId());

        assertThat(receipt.getStatus()).isEqualTo(ProductionReceiptStatus.PENDING_APPROVAL);
        assertThat(receipt.getSubmittedAt()).isNotNull();
        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("0");
        verifyNoInteractions(movementService, wipTransactionService);
    }

    @Test
    void submit_alreadySubmittedReceipt_shouldThrowStateConflict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = pendingReceipt(workOrder, workOrder.getOutputWarehouse(), new BigDecimal("10"));
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();

        assertThatThrownBy(() -> service.submit(workOrderId, receiptId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verify(receiptRepository, never()).save(any());
        verifyNoInteractions(movementService, wipTransactionService);
    }

    @Test
    void post_onBlockedWorkOrder_shouldThrow() {
        // B13: a BLOCKED work order must not be able to submit a production receipt.
        WorkOrder workOrder = workOrder(WorkOrderStatus.BLOCKED, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-BLOCKED")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        UUID workOrderId = workOrder.getWorkOrderId();
        ProductionReceiptPostRequest request = receiptRequest(warehouse, new BigDecimal("5"));

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-BLOCKED"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService, wipTransactionService);
        verify(receiptRepository, never()).save(any());
    }

    /**
     * Debt #25, fixed in D11. B53 completes the work order the instant cumulative good reaches the
     * plan, but {@code completedQuantity} — what has actually been warehoused — lags behind it, so
     * gating receipts on {@code canExecute()} stranded the last receipt of every work order planned
     * for exactly the quantity that had to be received. {@code COMPLETED} says the shop floor is
     * finished, not that the goods are in the racks (B13, split a second time).
     */
    @Test
    void post_onCompletedWorkOrder_receiptsTheOutputThatIsNotWarehousedYet() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.COMPLETED, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-COMPLETED")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(BigDecimal.ZERO);
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(workOrder.getWorkOrderId(), receiptRequest(warehouse, new BigDecimal("10")), "KEY-COMPLETED");

        ArgumentCaptor<ProductionReceipt> captor = ArgumentCaptor.forClass(ProductionReceipt.class);
        verify(receiptRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProductionReceiptStatus.DRAFT);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
    }

    /**
     * Nothing about D11 widens B16. The ceiling is still what the shop floor made and has not
     * warehoused yet, which is the one thing that keeps a {@code COMPLETED} work order from becoming
     * an open door — this is the gap that would open if the quantity check were ever moved behind
     * the status gate.
     */
    @Test
    void post_onCompletedWorkOrder_stillCannotExceedWhatWasProduced() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.COMPLETED, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        workOrder.setCompletedQuantity(new BigDecimal("8"));
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-COMPLETED-OVER")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(BigDecimal.ZERO);

        // 10 produced − 8 already warehoused leaves 2; asking for 3 must still fail.
        ProductionReceiptPostRequest request = receiptRequest(warehouse, new BigDecimal("3"));
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-COMPLETED-OVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED));

        verify(receiptRepository, never()).save(any());
        verifyNoInteractions(movementService, wipTransactionService);
    }

    /** D11 opened exactly one status. A cancelled work order is dead, and stays refused (B13). */
    @Test
    void post_onCancelledWorkOrder_shouldThrow() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.CANCELLED, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-CANCELLED")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        UUID workOrderId = workOrder.getWorkOrderId();
        ProductionReceiptPostRequest request = receiptRequest(warehouse, new BigDecimal("5"));

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-CANCELLED"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService, wipTransactionService);
        verify(receiptRepository, never()).save(any());
    }

    /**
     * The whole receipt lifecycle had to move to the new gate, not just {@code post}: leaving
     * {@code submit} or {@code approve} on {@code canExecute()} would let a draft be created on a
     * {@code COMPLETED} work order and then never be warehoused — the same bug, one step later.
     */
    @Test
    void submit_onCompletedWorkOrder_isAllowed() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.COMPLETED, new BigDecimal("10"));
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("10"), ProductionReceiptStatus.DRAFT, null);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(workOrder.getWorkOrderId(), receipt.getReceiptId());

        assertThat(receipt.getStatus()).isEqualTo(ProductionReceiptStatus.PENDING_APPROVAL);
        verifyNoInteractions(movementService, wipTransactionService);
    }

    @Test
    void approve_onCompletedWorkOrder_warehousesTheOutputAndRaisesCompletedQuantity() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.COMPLETED, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        ProductionReceipt receipt = pendingReceipt(workOrder, warehouse, new BigDecimal("10"));
        StockMovement movement = movement(workOrder.getProductItem(), warehouse);

        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(movementService.receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.HOLD)))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(workOrder.getWorkOrderId(), receipt.getReceiptId());

        verify(movementService).receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.HOLD));
        assertThat(receipt.getStatus()).isEqualTo(ProductionReceiptStatus.APPROVED);
        // This is the number debt #25 could never reach: everything produced is now warehoused.
        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("10");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        verify(wipTransactionService).recordOutputReceipted(eq(workOrder), eq(new BigDecimal("10")), any());
    }

    @Test
    void post_idempotency_sameKeyTwice_returnsExistingPendingReceipt() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt existing = ProductionReceipt.builder()
                .receiptId(UUID.randomUUID())
                .workOrder(workOrder)
                .status(ProductionReceiptStatus.PENDING_APPROVAL)
                .idempotencyKey("KEY-IDEM")
                .lines(new ArrayList<>())
                .build();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-IDEM")).thenReturn(Optional.of(existing));

        service.post(workOrder.getWorkOrderId(), receiptRequest(workOrder.getOutputWarehouse(), new BigDecimal("10")), "KEY-IDEM");

        verify(receiptRepository, never()).save(any());
        verifyNoInteractions(movementService, workOrderRepository);
    }

    @Test
    void post_exceedProducedGoodIncludingOpenReceipts_shouldThrow() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-PENDING")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        // 7 already claimed by open receipts, so only 3 of the 10 produced remain (B16 after F5).
        when(receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(new BigDecimal("7"));

        ProductionReceiptPostRequest request = receiptRequest(warehouse, new BigDecimal("4"));
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-PENDING"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED));

        verifyNoInteractions(movementService);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void post_overProducedGoodQuantity_failsBeforeMovement() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        when(receiptRepository.findWithLinesByIdempotencyKey("KEY-OVER")).thenReturn(Optional.empty());
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), workOrder.getPlant().getPlantId()))
                .thenReturn(warehouse);
        when(receiptRepository.sumOpenQuantityByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(BigDecimal.ZERO);

        ProductionReceiptPostRequest request = receiptRequest(warehouse, new BigDecimal("11"));
        UUID workOrderId = workOrder.getWorkOrderId();

        assertThatThrownBy(() -> service.post(workOrderId, request, "KEY-OVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED));

        verify(movementService, never()).receive(any(), anyString(), any());
    }

    @Test
    void approve_shouldCreateReceiveMovementWithHoldLot_withoutCompletingWorkOrder() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        ProductionReceipt receipt = pendingReceipt(workOrder, warehouse, new BigDecimal("10"));
        StockMovement movement = movement(workOrder.getProductItem(), warehouse);

        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(movementService.receive(any(InventoryReceiveCommand.class),
                eq(receipt.getIdempotencyKey() + ":approve:L1"), eq(LotStatus.HOLD)))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(workOrder.getWorkOrderId(), receipt.getReceiptId());

        verify(movementService).receive(any(InventoryReceiveCommand.class),
                eq(receipt.getIdempotencyKey() + ":approve:L1"), eq(LotStatus.HOLD));
        assertThat(receipt.getStatus()).isEqualTo(ProductionReceiptStatus.APPROVED);
        assertThat(receipt.getApprovedAt()).isNotNull();
        assertThat(receipt.getLines().get(0).getStockMovement()).isSameAs(movement);
        // B17 after F5: approval records that output was warehoused. Completion belongs to
        // Production Execution alone, so the status must NOT move even at full quantity.
        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("10");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(wipTransactionService).recordOutputReceipted(eq(workOrder), eq(new BigDecimal("10")), any());
        // F6: approval is not a fulfilment trigger either — the goods are still on HOLD and cannot
        // ship. Only a QC disposition of AVAILABLE moves a sales order (spec §7.1).
        verifyNoInteractions(allocationService);
    }

    @Test
    void approve_partialQuantity_shouldNotCompleteWorkOrder() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        ProductionReceipt receipt = pendingReceipt(workOrder, warehouse, new BigDecimal("4"));
        StockMovement movement = movement(workOrder.getProductItem(), warehouse);

        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(movementService.receive(any(InventoryReceiveCommand.class), anyString(), eq(LotStatus.HOLD)))
                .thenReturn(new InventoryMovementResult(movement, true));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(workOrder.getWorkOrderId(), receipt.getReceiptId());

        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("4");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    }

    @Test
    void approve_alreadyApproved_shouldThrowStateConflict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = pendingReceipt(workOrder, workOrder.getOutputWarehouse(), new BigDecimal("10"));
        receipt.setStatus(ProductionReceiptStatus.APPROVED);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();

        assertThatThrownBy(() -> service.approve(workOrderId, receiptId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService, wipTransactionService);
    }

    @Test
    void approve_draftReceipt_shouldThrowStateConflict() {
        // A DRAFT must go through submit first — approving it would skip the hand-off entirely.
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("10"), ProductionReceiptStatus.DRAFT, null);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();

        assertThatThrownBy(() -> service.approve(workOrderId, receiptId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService, wipTransactionService);
    }

    @Test
    void approve_receiptOfAnotherWorkOrder_shouldThrow() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = pendingReceipt(workOrder, workOrder.getOutputWarehouse(), new BigDecimal("10"));
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID otherWorkOrderId = UUID.randomUUID();
        UUID receiptId = receipt.getReceiptId();

        assertThatThrownBy(() -> service.approve(otherWorkOrderId, receiptId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService);
    }

    @Test
    void reject_pendingReceipt_shouldRejectWithoutInventoryImpact() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = pendingReceipt(workOrder, workOrder.getOutputWarehouse(), new BigDecimal("10"));
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reject(workOrder.getWorkOrderId(), receipt.getReceiptId(),
                new ProductionReceiptRejectRequest(" Quality failed "));

        assertThat(receipt.getStatus()).isEqualTo(ProductionReceiptStatus.REJECTED);
        assertThat(receipt.getRejectReason()).isEqualTo("Quality failed");
        assertThat(receipt.getRejectedAt()).isNotNull();
        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("0");
        verifyNoInteractions(movementService, wipTransactionService);
    }

    @Test
    void reject_blankReason_shouldThrow() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = pendingReceipt(workOrder, workOrder.getOutputWarehouse(), new BigDecimal("10"));
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();
        ProductionReceiptRejectRequest request = new ProductionReceiptRejectRequest("   ");

        assertThatThrownBy(() -> service.reject(workOrderId, receiptId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.APPROVAL_REASON_REQUIRED));

        verify(receiptRepository, never()).save(any());
    }

    // ── QC disposition (spec §6.1) ─────────────────────────────────────────

    @Test
    void qcDisposition_available_releasesLotAndRecordsDecision() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"), true);
        InventoryLot lot = lot(workOrder.getProductItem(), LotStatus.HOLD);
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("10"), ProductionReceiptStatus.APPROVED, lot);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.qcDisposition(workOrder.getWorkOrderId(), receipt.getReceiptId(),
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, " Passed visual "));

        ArgumentCaptor<LotStatusChangeCommand> commandCaptor =
                ArgumentCaptor.forClass(LotStatusChangeCommand.class);
        verify(movementService).changeLotStatus(commandCaptor.capture(),
                eq(receipt.getIdempotencyKey() + ":qc:L1"));
        assertThat(commandCaptor.getValue().newStatus()).isEqualTo(LotStatus.AVAILABLE);
        assertThat(commandCaptor.getValue().lotId()).isEqualTo(lot.getLotId());
        assertThat(commandCaptor.getValue().quantity()).isEqualByComparingTo("10");

        ArgumentCaptor<QualityDisposition> dispositionCaptor =
                ArgumentCaptor.forClass(QualityDisposition.class);
        verify(dispositionRepository).save(dispositionCaptor.capture());
        assertThat(dispositionCaptor.getValue().getResult()).isEqualTo(QualityDispositionResult.AVAILABLE);
        assertThat(dispositionCaptor.getValue().getReason()).isEqualTo("Passed visual");
        assertThat(dispositionCaptor.getValue().getLot()).isSameAs(lot);

        // The receipt stays APPROVED: QC rules on the lot, it never un-does received stock.
        assertThat(receipt.getStatus()).isEqualTo(ProductionReceiptStatus.APPROVED);
        assertThat(receipt.getQcResult()).isEqualTo(QualityDispositionResult.AVAILABLE);
        assertThat(receipt.getQcAt()).isNotNull();
        verifyNoInteractions(wipTransactionService);
    }

    @Test
    void qcDisposition_rejected_sendsLotToRejectedWithoutTouchingWorkOrder() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"), true);
        InventoryLot lot = lot(workOrder.getProductItem(), LotStatus.HOLD);
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("10"), ProductionReceiptStatus.APPROVED, lot);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.qcDisposition(workOrder.getWorkOrderId(), receipt.getReceiptId(),
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.REJECTED, "Failed hardness"));

        ArgumentCaptor<LotStatusChangeCommand> commandCaptor =
                ArgumentCaptor.forClass(LotStatusChangeCommand.class);
        verify(movementService).changeLotStatus(commandCaptor.capture(), anyString());
        assertThat(commandCaptor.getValue().newStatus()).isEqualTo(LotStatus.REJECTED);
        assertThat(receipt.getQcResult()).isEqualTo(QualityDispositionResult.REJECTED);
        assertThat(workOrder.getCompletedQuantity()).isEqualByComparingTo("0");
        verify(movementService, never()).receive(any(), anyString(), any());
    }

    /**
     * Spec §7.1 / invariant B62: releasing the lot to AVAILABLE is the one and only trigger that
     * turns produced goods into fulfilled sales order quantity.
     */
    @Test
    void qcDisposition_available_fulfilsTheAllocatedSalesOrderQuantity() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"), true);
        InventoryLot lot = lot(workOrder.getProductItem(), LotStatus.HOLD);
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("7"), ProductionReceiptStatus.APPROVED, lot);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.qcDisposition(workOrder.getWorkOrderId(), receipt.getReceiptId(),
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, "Passed"));

        // The quantity handed over is what QC just released, not the work order's planned or
        // completed quantity (CLAUDE.md §0.5 — those three columns mean different things).
        verify(allocationService).fulfill(same(workOrder), argThat(quantity ->
                quantity.compareTo(new BigDecimal("7")) == 0));
    }

    /**
     * Rule R5: the REJECTED branch must prove it never reaches fulfilment. Rejected output stays on
     * hand but unusable — a customer order it cannot ship against must not move.
     */
    @Test
    void qcDisposition_rejected_neverReachesFulfilment() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"), true);
        InventoryLot lot = lot(workOrder.getProductItem(), LotStatus.HOLD);
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("7"), ProductionReceiptStatus.APPROVED, lot);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.qcDisposition(workOrder.getWorkOrderId(), receipt.getReceiptId(),
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.REJECTED, "Failed hardness"));

        verifyNoInteractions(allocationService);
    }

    @Test
    void qcDisposition_onPendingApprovalReceipt_shouldThrowStateConflict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"), true);
        InventoryLot lot = lot(workOrder.getProductItem(), LotStatus.HOLD);
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("10"), ProductionReceiptStatus.PENDING_APPROVAL, lot);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();
        ProductionReceiptQcDispositionRequest request =
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, "Passed");

        assertThatThrownBy(() -> service.qcDisposition(workOrderId, receiptId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService, dispositionRepository);
    }

    @Test
    void qcDisposition_blankReason_shouldThrowApprovalReasonRequired() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"), true);
        InventoryLot lot = lot(workOrder.getProductItem(), LotStatus.HOLD);
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("10"), ProductionReceiptStatus.APPROVED, lot);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();
        ProductionReceiptQcDispositionRequest request =
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, "   ");

        assertThatThrownBy(() -> service.qcDisposition(workOrderId, receiptId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.APPROVAL_REASON_REQUIRED));

        assertThat(lot.getStatus()).isEqualTo(LotStatus.HOLD);
        verifyNoInteractions(movementService, dispositionRepository);
    }

    @Test
    void qcDisposition_twice_shouldThrowStateConflict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"), true);
        InventoryLot lot = lot(workOrder.getProductItem(), LotStatus.AVAILABLE);
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("10"), ProductionReceiptStatus.APPROVED, lot);
        receipt.recordQcDecision(QualityDispositionResult.AVAILABLE, "Passed", Instant.now(), null);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();
        ProductionReceiptQcDispositionRequest request =
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.REJECTED, "Changed my mind");

        assertThatThrownBy(() -> service.qcDisposition(workOrderId, receiptId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService, dispositionRepository);
    }

    // ── QC disposition on output that is not lot-tracked (D5, debt #17) ────

    /**
     * Replaces {@code qcDisposition_nonLotTrackedOutput_shouldThrowStateConflict} (R10: the old case
     * asserted exactly the behaviour D5 removes, so it is rewritten rather than deleted). Before D5
     * this receipt was refused with {@code STATE_CONFLICT}, which left every non-lot-tracked finished
     * good stranded: allocations existed but nothing could ever fulfil them.
     */
    @Test
    void qcDisposition_available_onOutputWithoutALot_fulfilsWithoutTouchingLotsOrStock() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("6"), ProductionReceiptStatus.APPROVED, null);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.qcDisposition(workOrder.getWorkOrderId(), receipt.getReceiptId(),
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, " Passed visual "));

        // The trap this test exists for: dispositionedQuantity used to be summed from lot lines only,
        // so a receipt without a lot would hand fulfilment a silent zero. Assert the real number.
        verify(allocationService).fulfill(same(workOrder), argThat(quantity ->
                quantity.compareTo(new BigDecimal("6")) == 0));
        // No lot exists, so there is no lot status to change and no per-lot audit row to write
        // (decision A2: quality_dispositions is one row per lot by definition).
        verify(movementService, never()).changeLotStatus(any(), anyString());
        // AVAILABLE changes nothing in the ledger: the goods have been in free stock since approval.
        verify(movementService, never()).adjust(any(), anyString());
        verifyNoInteractions(dispositionRepository, wipTransactionService);

        assertThat(receipt.getStatus()).isEqualTo(ProductionReceiptStatus.APPROVED);
        assertThat(receipt.getQcResult()).isEqualTo(QualityDispositionResult.AVAILABLE);
        assertThat(receipt.getQcReason()).isEqualTo("Passed visual");
        assertThat(receipt.getQcAt()).isNotNull();
    }

    /**
     * B39 rewritten in D5: defective output that is not lot-tracked is already usable, so REJECTED has
     * to take it back out of stock or the ledger would let a customer ship it.
     */
    @Test
    void qcDisposition_rejected_onOutputWithoutALot_withdrawsTheStockAndNeverFulfils() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        Warehouse warehouse = workOrder.getOutputWarehouse();
        ProductionReceipt receipt = receipt(workOrder, warehouse,
                new BigDecimal("6"), ProductionReceiptStatus.APPROVED, null);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(receiptRepository.save(any(ProductionReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.qcDisposition(workOrder.getWorkOrderId(), receipt.getReceiptId(),
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.REJECTED, "Failed hardness"));

        ArgumentCaptor<InventoryAdjustCommand> commandCaptor =
                ArgumentCaptor.forClass(InventoryAdjustCommand.class);
        verify(movementService).adjust(commandCaptor.capture(),
                eq(receipt.getIdempotencyKey() + ":qc-reject:L1"));
        InventoryAdjustCommand command = commandCaptor.getValue();
        // A negative delta is what makes InventoryMovementService emit ADJUST_OUT / direction OUT.
        assertThat(command.quantityDelta()).isEqualByComparingTo("-6");
        assertThat(command.itemId()).isEqualTo(workOrder.getProductItem().getItemId());
        assertThat(command.warehouseId()).isEqualTo(warehouse.getWarehouseId());
        assertThat(command.lotId()).isNull();
        assertThat(command.lotCode()).isNull();
        assertThat(command.reason()).isEqualTo("Failed hardness");
        assertThat(command.referenceId()).isEqualTo(workOrder.getWorkOrderId().toString());

        verify(movementService, never()).changeLotStatus(any(), anyString());
        assertThat(receipt.getQcResult()).isEqualTo(QualityDispositionResult.REJECTED);
        // B62 still holds on the new path: rejected output never reaches a sales order.
        verifyNoInteractions(allocationService, dispositionRepository);
    }

    /** B38 is not relaxed on the new path: a receipt still has to be APPROVED to be judged. */
    @Test
    void qcDisposition_onOutputWithoutALot_stillRequiresAnApprovedReceipt() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("6"), ProductionReceiptStatus.PENDING_APPROVAL, null);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();
        ProductionReceiptQcDispositionRequest request =
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, "Passed");

        assertThatThrownBy(() -> service.qcDisposition(workOrderId, receiptId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService, allocationService);
    }

    /** B38 is not relaxed on the new path: one verdict per receipt, so fulfilment cannot double-count. */
    @Test
    void qcDisposition_onOutputWithoutALot_stillRefusesASecondVerdict() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("6"), ProductionReceiptStatus.APPROVED, null);
        receipt.recordQcDecision(QualityDispositionResult.AVAILABLE, "Passed", Instant.now(), null);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();
        ProductionReceiptQcDispositionRequest request =
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, "Passed again");

        assertThatThrownBy(() -> service.qcDisposition(workOrderId, receiptId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(movementService, allocationService);
    }

    /** B38 is not relaxed on the new path: a verdict without a reason is not auditable. */
    @Test
    void qcDisposition_onOutputWithoutALot_stillRequiresAReason() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"));
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("6"), ProductionReceiptStatus.APPROVED, null);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();
        ProductionReceiptQcDispositionRequest request =
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.REJECTED, "   ");

        assertThatThrownBy(() -> service.qcDisposition(workOrderId, receiptId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.APPROVAL_REASON_REQUIRED));

        assertThat(receipt.getQcResult()).isNull();
        verifyNoInteractions(movementService, allocationService);
    }

    @Test
    void qcDisposition_lotNoLongerOnHold_shouldThrowLotNotEligibleBeforeAnyChange() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.IN_PROGRESS, new BigDecimal("10"), true);
        InventoryLot lot = lot(workOrder.getProductItem(), LotStatus.EXPIRED);
        ProductionReceipt receipt = receipt(workOrder, workOrder.getOutputWarehouse(),
                new BigDecimal("10"), ProductionReceiptStatus.APPROVED, lot);
        when(receiptRepository.findWithLinesByReceiptId(receipt.getReceiptId())).thenReturn(Optional.of(receipt));

        UUID workOrderId = workOrder.getWorkOrderId();
        UUID receiptId = receipt.getReceiptId();
        ProductionReceiptQcDispositionRequest request =
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, "Passed");

        assertThatThrownBy(() -> service.qcDisposition(workOrderId, receiptId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOT_NOT_ELIGIBLE));

        assertThat(lot.getStatus()).isEqualTo(LotStatus.EXPIRED);
        assertThat(receipt.getQcResult()).isNull();
        verifyNoInteractions(movementService, dispositionRepository);
    }

    private ProductionReceiptPostRequest receiptRequest(Warehouse warehouse, BigDecimal quantity) {
        return new ProductionReceiptPostRequest(
                warehouse.getWarehouseId(), null, null, null, quantity, "Complete", "Receipt");
    }

    private ProductionReceiptPostRequest serialReceiptRequest(Warehouse warehouse, BigDecimal quantity, String serialNumber) {
        return new ProductionReceiptPostRequest(
                warehouse.getWarehouseId(), null, null, serialNumber, quantity, "Complete", "Receipt");
    }

    private ProductionReceipt pendingReceipt(WorkOrder workOrder, Warehouse warehouse, BigDecimal quantity) {
        return receipt(workOrder, warehouse, quantity, ProductionReceiptStatus.PENDING_APPROVAL, null);
    }

    /** Mirrors {@link #receipt} but for the serial-tracked path: the line carries a requested code
     *  instead of an already-resolved lot, exactly like {@code postNew} leaves it before approval. */
    private ProductionReceipt receiptWithSerial(WorkOrder workOrder,
                                                Warehouse warehouse,
                                                BigDecimal quantity,
                                                ProductionReceiptStatus status,
                                                String requestedSerialCode) {
        ProductionReceipt receipt = ProductionReceipt.builder()
                .receiptId(UUID.randomUUID())
                .workOrder(workOrder)
                .status(status)
                .idempotencyKey("KEY-PENDING-RECEIPT")
                .lines(new ArrayList<>())
                .build();
        receipt.getLines().add(ProductionReceiptLine.builder()
                .receiptLineId(UUID.randomUUID())
                .receipt(receipt)
                .item(workOrder.getProductItem())
                .warehouse(warehouse)
                .requestedSerialCode(requestedSerialCode)
                .quantity(quantity)
                .build());
        return receipt;
    }

    /** An {@code APPROVED} receipt whose line already carries the resolved serial — the shape QC acts on. */
    private ProductionReceipt receiptWithApprovedSerial(WorkOrder workOrder,
                                                        Warehouse warehouse,
                                                        BigDecimal quantity,
                                                        SerialNumber serial) {
        ProductionReceipt receipt = ProductionReceipt.builder()
                .receiptId(UUID.randomUUID())
                .workOrder(workOrder)
                .status(ProductionReceiptStatus.APPROVED)
                .idempotencyKey("KEY-PENDING-RECEIPT")
                .lines(new ArrayList<>())
                .build();
        receipt.getLines().add(ProductionReceiptLine.builder()
                .receiptLineId(UUID.randomUUID())
                .receipt(receipt)
                .item(workOrder.getProductItem())
                .warehouse(warehouse)
                .serial(serial)
                .quantity(quantity)
                .build());
        return receipt;
    }

    private ProductionReceipt receipt(WorkOrder workOrder,
                                      Warehouse warehouse,
                                      BigDecimal quantity,
                                      ProductionReceiptStatus status,
                                      InventoryLot lot) {
        ProductionReceipt receipt = ProductionReceipt.builder()
                .receiptId(UUID.randomUUID())
                .workOrder(workOrder)
                .status(status)
                .idempotencyKey("KEY-PENDING-RECEIPT")
                .lines(new ArrayList<>())
                .build();
        receipt.getLines().add(ProductionReceiptLine.builder()
                .receiptLineId(UUID.randomUUID())
                .receipt(receipt)
                .item(workOrder.getProductItem())
                .warehouse(warehouse)
                .lot(lot)
                .quantity(quantity)
                .build());
        return receipt;
    }

    private InventoryLot lot(Item item, LotStatus status) {
        return InventoryLot.builder()
                .lotId(UUID.randomUUID())
                .item(item)
                .lotCode("LOT-001")
                .status(status)
                .receivedAt(Instant.now())
                .build();
    }

    private StockMovement movement(Item item, Warehouse warehouse) {
        return StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("KEY")
                .createdAt(Instant.now())
                .build();
    }

    private WorkOrder workOrder(WorkOrderStatus status, BigDecimal plannedQuantity) {
        return workOrder(status, plannedQuantity, false);
    }

    private WorkOrder workOrder(WorkOrderStatus status, BigDecimal plannedQuantity, boolean lotTrackedOutput) {
        UUID companyId = UUID.randomUUID();
        Plant plant = plant(UUID.randomUUID(), companyId);
        Item product = item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD);
        product.setLotTracked(lotTrackedOutput);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        BomHeader bom = BomHeader.builder()
                .bomId(UUID.randomUUID())
                .company(product.getCompany())
                .parentItem(product)
                .revision("R1")
                .status(BomStatus.ACTIVE)
                .lines(new ArrayList<>())
                .build();
        return WorkOrder.builder()
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
                // F5: a receipt draws from what the shop floor reported, not from the plan. The
                // fixture assumes the whole planned quantity was produced and none receipted yet.
                .actualGoodQuantity(plannedQuantity)
                .actualScrapQuantity(BigDecimal.ZERO)
                .actualReworkQuantity(BigDecimal.ZERO)
                .status(status)
                .componentLines(new ArrayList<>())
                .build();
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
