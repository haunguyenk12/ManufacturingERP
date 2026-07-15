package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoodsReceiptService tests")
class GoodsReceiptServiceTest {

    @Mock GoodsReceiptRepository goodsReceiptRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock InventoryMovementService inventoryMovementService;

    GoodsReceiptService service;

    @BeforeEach
    void setUp() {
        service = new GoodsReceiptService(
                goodsReceiptRepository,
                purchaseOrderRepository,
                inventoryMovementService,
                new PurchasingMapper());
    }

    @Test
    void postPartialReceipt_callsInventoryReceiveAndUpdatesPoStatus() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.SENT, new BigDecimal("10"), BigDecimal.ZERO);
        PurchaseOrderLine line = order.getLines().get(0);
        StockMovement movement = movement(line.getItem(), order.getWarehouse(), new BigDecimal("4"));
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.empty());
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            if (receipt.getGoodsReceiptId() == null) {
                receipt.setGoodsReceiptId(UUID.randomUUID());
            }
            receipt.getLines().forEach(receiptLine -> receiptLine.setGoodsReceiptLineId(UUID.randomUUID()));
            return receipt;
        });
        when(inventoryMovementService.receive(any(InventoryReceiveCommand.class), eq("GR-KEY:L1")))
                .thenReturn(new InventoryMovementResult(movement, true));

        GoodsReceiptResponse response = service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", "Receive", List.of(new GoodsReceiptLineRequest(
                line.getPurchaseOrderLineId(), null, null, new BigDecimal("4")))), "GR-KEY");

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).stockMovementId()).isEqualTo(movement.getMovementId());
        assertThat(line.getReceivedQuantity()).isEqualByComparingTo("4");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        verify(inventoryMovementService).receive(argThat(command ->
                command.itemId().equals(line.getItem().getItemId())
                        && command.warehouseId().equals(order.getWarehouse().getWarehouseId())
                        && command.quantity().compareTo(new BigDecimal("4")) == 0
                        && command.referenceType().equals("GOODS_RECEIPT")), eq("GR-KEY:L1"));
    }

    @Test
    void postFullReceipt_marksPoReceived() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.SENT, new BigDecimal("10"), BigDecimal.ZERO);
        PurchaseOrderLine line = order.getLines().get(0);
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.empty());
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            if (receipt.getGoodsReceiptId() == null) {
                receipt.setGoodsReceiptId(UUID.randomUUID());
            }
            return receipt;
        });
        when(inventoryMovementService.receive(any(InventoryReceiveCommand.class), anyString()))
                .thenReturn(new InventoryMovementResult(
                        movement(line.getItem(), order.getWarehouse(), new BigDecimal("10")), true));

        service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", null, List.of(new GoodsReceiptLineRequest(
                line.getPurchaseOrderLineId(), null, null, new BigDecimal("10")))), "GR-KEY");

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
    }

    @Test
    void postOverRemainingQuantity_failsBeforeInventoryReceive() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.SENT, new BigDecimal("10"), new BigDecimal("8"));
        PurchaseOrderLine line = order.getLines().get(0);
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.empty());
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            receipt.setGoodsReceiptId(UUID.randomUUID());
            return receipt;
        });

        assertThatThrownBy(() -> service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", null, List.of(new GoodsReceiptLineRequest(
                line.getPurchaseOrderLineId(), null, null, new BigDecimal("3")))), "GR-KEY"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    void postDuplicateIdempotency_returnsExistingReceiptWithoutInventoryReceive() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.SENT, new BigDecimal("10"), BigDecimal.ZERO);
        GoodsReceipt existing = GoodsReceipt.builder()
                .goodsReceiptId(UUID.randomUUID())
                .purchaseOrder(order)
                .warehouse(order.getWarehouse())
                .receiptNo("GR-001")
                .status(GoodsReceiptStatus.POSTED)
                .postedAt(Instant.now())
                .idempotencyKey("GR-KEY")
                .lines(new ArrayList<>())
                .build();
        when(goodsReceiptRepository.findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
                order.getPurchaseOrderId(), "GR-KEY")).thenReturn(Optional.of(existing));

        GoodsReceiptResponse response = service.post(order.getPurchaseOrderId(), new GoodsReceiptPostRequest(
                "GR-001", null, List.of(new GoodsReceiptLineRequest(
                order.getLines().get(0).getPurchaseOrderLineId(), null, null, BigDecimal.ONE))), "GR-KEY");

        assertThat(response.goodsReceiptId()).isEqualTo(existing.getGoodsReceiptId());
        verifyNoInteractions(inventoryMovementService, purchaseOrderRepository);
    }

    @Test
    void cancelPosted_shouldCancelAndCreateReversalMovementAndUpdatePOStatus() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.RECEIVED, new BigDecimal("10"), new BigDecimal("10"));
        PurchaseOrderLine poLine = order.getLines().get(0);
        Item item = poLine.getItem();
        Warehouse warehouse = order.getWarehouse();

        UUID grId = UUID.randomUUID();
        GoodsReceiptLine grLine = GoodsReceiptLine.builder()
                .goodsReceiptLineId(UUID.randomUUID())
                .purchaseOrderLine(poLine)
                .item(item)
                .receivedQuantity(new BigDecimal("10"))
                .stockMovement(movement(item, warehouse, new BigDecimal("10")))
                .build();

        GoodsReceipt receipt = GoodsReceipt.builder()
                .goodsReceiptId(grId)
                .purchaseOrder(order)
                .warehouse(warehouse)
                .receiptNo("GR-001")
                .status(GoodsReceiptStatus.POSTED)
                .postedAt(Instant.now())
                .idempotencyKey("GR-KEY")
                .lines(new ArrayList<>(List.of(grLine)))
                .build();
        grLine.setGoodsReceipt(receipt);

        when(goodsReceiptRepository.findWithDetailsByGoodsReceiptId(grId)).thenReturn(Optional.of(receipt));
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryMovementService.reverseReceive(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryMovementResult(movement(item, warehouse, new BigDecimal("10")), true));

        GoodsReceiptResponse response = service.cancel(grId, new GoodsReceiptCancelRequest("damaged batch"));

        assertThat(response.status()).isEqualTo(GoodsReceiptStatus.CANCELLED.name());
        assertThat(response.cancelNote()).isEqualTo("damaged batch");
        assertThat(response.cancelledAt()).isNotNull();
        // PO received_quantity should go back to 0 → SENT
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.SENT);
        assertThat(poLine.getReceivedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(inventoryMovementService).reverseReceive(
                eq(item.getItemId()),
                eq(warehouse.getWarehouseId()),
                isNull(),
                eq(new BigDecimal("10")),
                eq("GOODS_RECEIPT_CANCEL"),
                eq(grId.toString()),
                anyString());
        verify(purchaseOrderRepository).save(order);
    }

    @Test
    void cancelAlreadyCancelled_shouldThrowOperationNotAllowed() {
        UUID grId = UUID.randomUUID();
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.SENT, new BigDecimal("10"), BigDecimal.ZERO);
        GoodsReceipt receipt = GoodsReceipt.builder()
                .goodsReceiptId(grId)
                .purchaseOrder(order)
                .warehouse(order.getWarehouse())
                .receiptNo("GR-001")
                .status(GoodsReceiptStatus.CANCELLED)
                .postedAt(Instant.now())
                .idempotencyKey("GR-KEY")
                .lines(new ArrayList<>())
                .build();

        when(goodsReceiptRepository.findWithDetailsByGoodsReceiptId(grId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.cancel(grId, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(inventoryMovementService, purchaseOrderRepository);
    }

    private StockMovement movement(Item item, Warehouse warehouse, BigDecimal quantity) {
        return StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(item)
                .warehouse(warehouse)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(quantity)
                .referenceType("GOODS_RECEIPT")
                .referenceId(UUID.randomUUID().toString())
                .idempotencyKey(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .build();
    }

    private PurchaseOrder purchaseOrder(PurchaseOrderStatus status,
                                        BigDecimal orderedQuantity,
                                        BigDecimal receivedQuantity) {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item item = item(UUID.randomUUID(), company);
        PurchaseOrder order = PurchaseOrder.builder()
                .purchaseOrderId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .supplier(supplier(UUID.randomUUID(), company))
                .purchaseOrderNo("PO-001")
                .status(status)
                .orderDate(LocalDate.now())
                .expectedDate(LocalDate.now().plusDays(7))
                .lines(new ArrayList<>())
                .build();
        order.getLines().add(PurchaseOrderLine.builder()
                .purchaseOrderLineId(UUID.randomUUID())
                .purchaseOrder(order)
                .item(item)
                .orderedQuantity(orderedQuantity)
                .receivedQuantity(receivedQuantity)
                .expectedDate(order.getExpectedDate())
                .build());
        return order;
    }

    private Item item(UUID itemId, Company company) {
        return Item.builder()
                .itemId(itemId)
                .company(company)
                .code("ITEM")
                .name("Item")
                .type(ItemType.RAW_MATERIAL)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private Supplier supplier(UUID supplierId, Company company) {
        return Supplier.builder()
                .supplierId(supplierId)
                .company(company)
                .code("SUP")
                .name("Supplier")
                .status(SupplierStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, Plant plant) {
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant)
                .code("WH1")
                .name("Warehouse 1")
                .type(WarehouseType.GENERAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Plant plant(UUID plantId, Company company) {
        return Plant.builder()
                .plantId(plantId)
                .company(company)
                .code("P1")
                .name("Plant 1")
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
