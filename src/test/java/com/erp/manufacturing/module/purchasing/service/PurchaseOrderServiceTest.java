package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseRequisitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseOrderService tests")
class PurchaseOrderServiceTest {

    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock PurchaseRequisitionRepository purchaseRequisitionRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemLookupService itemLookupService;
    @Mock SupplierService supplierService;

    PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(
                purchaseOrderRepository,
                purchaseRequisitionRepository,
                organizationLookupService,
                itemLookupService,
                supplierService,
                new PurchasingMapper());
    }

    @Test
    void createPurchaseOrder_success() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Supplier supplier = supplier(UUID.randomUUID(), company);
        Item item = item(UUID.randomUUID(), company);
        when(supplierService.findActiveSupplier(supplier.getSupplierId())).thenReturn(supplier);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(organizationLookupService.getActiveWarehouse(warehouse.getWarehouseId())).thenReturn(warehouse);
        when(purchaseOrderRepository.existsByCompanyCompanyIdAndPurchaseOrderNo(company.getCompanyId(), "PO-001"))
                .thenReturn(false);
        when(itemLookupService.getActiveItem(item.getItemId())).thenReturn(item);
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setPurchaseOrderId(UUID.randomUUID());
            order.getLines().forEach(line -> line.setPurchaseOrderLineId(UUID.randomUUID()));
            return order;
        });

        PurchaseOrderResponse response = service.create(new PurchaseOrderCreateRequest(
                company.getCompanyId(),
                plant.getPlantId(),
                warehouse.getWarehouseId(),
                supplier.getSupplierId(),
                "PO-001",
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                null,
                null,
                List.of(new PurchaseOrderLineRequest(
                        null, item.getItemId(), new BigDecimal("10"), BigDecimal.ZERO, "USD", null))));

        assertThat(response.status()).isEqualTo(PurchaseOrderStatus.DRAFT.name());
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).orderedQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void sendDraftOrder_success() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.DRAFT);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(order)).thenReturn(order);

        PurchaseOrderResponse response = service.send(order.getPurchaseOrderId());

        assertThat(response.status()).isEqualTo(PurchaseOrderStatus.SENT.name());
    }

    @Test
    void cancelSentOrder_fails() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.SENT);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(order.getPurchaseOrderId()))
                .isInstanceOf(AppException.class)
                // D7: purchase order status conflict is 409 STATE_CONFLICT, not 422
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    private PurchaseOrder purchaseOrder(PurchaseOrderStatus status) {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Supplier supplier = supplier(UUID.randomUUID(), company);
        PurchaseOrder order = PurchaseOrder.builder()
                .purchaseOrderId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .supplier(supplier)
                .purchaseOrderNo("PO-001")
                .status(status)
                .orderDate(LocalDate.now())
                .expectedDate(LocalDate.now().plusDays(7))
                .lines(new ArrayList<>())
                .build();
        order.getLines().add(PurchaseOrderLine.builder()
                .purchaseOrderLineId(UUID.randomUUID())
                .purchaseOrder(order)
                .item(item(UUID.randomUUID(), company))
                .orderedQuantity(new BigDecimal("10"))
                .receivedQuantity(BigDecimal.ZERO)
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
