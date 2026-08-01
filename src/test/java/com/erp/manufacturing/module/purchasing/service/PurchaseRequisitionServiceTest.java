package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionRepository;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseRequisitionService tests")
class PurchaseRequisitionServiceTest {

    @Mock PurchaseRequisitionRepository purchaseRequisitionRepository;
    @Mock SupplySuggestionRepository supplySuggestionRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemLookupService itemLookupService;
    @Mock SupplierService supplierService;
    @Mock PurchaseOrderService purchaseOrderService;

    PurchaseRequisitionService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseRequisitionService(
                purchaseRequisitionRepository,
                supplySuggestionRepository,
                organizationLookupService,
                itemLookupService,
                supplierService,
                purchaseOrderService,
                new PurchasingMapper());
    }

    @Test
    void convertFromApprovedPurchaseSuggestion_createsRequisitionAndMarksSuggestionConverted() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item item = item(UUID.randomUUID(), company);
        Supplier supplier = supplier(UUID.randomUUID(), company);
        SupplySuggestion suggestion = suggestion(company, plant, warehouse, item,
                SupplySuggestionType.PURCHASE_REQUISITION, SupplySuggestionStatus.APPROVED);

        when(supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestion.getSupplySuggestionId()))
                .thenReturn(Optional.of(suggestion));
        when(supplierService.findActiveSupplier(supplier.getSupplierId())).thenReturn(supplier);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(organizationLookupService.getActiveWarehouse(warehouse.getWarehouseId())).thenReturn(warehouse);
        when(purchaseRequisitionRepository.existsByCompanyCompanyIdAndRequisitionNo(company.getCompanyId(), "PR-001"))
                .thenReturn(false);
        when(purchaseRequisitionRepository.save(any(PurchaseRequisition.class))).thenAnswer(invocation -> {
            PurchaseRequisition requisition = invocation.getArgument(0);
            requisition.setPurchaseRequisitionId(UUID.randomUUID());
            requisition.getLines().forEach(line -> line.setPurchaseRequisitionLineId(UUID.randomUUID()));
            return requisition;
        });

        PurchaseRequisitionResponse response = service.convertFromSuggestion(
                suggestion.getSupplySuggestionId(),
                new PurchaseRequisitionFromSuggestionRequest("PR-001", null, supplier.getSupplierId(), "MRP"));

        assertThat(response.sourceType()).isEqualTo("MRP_SUGGESTION");
        assertThat(response.lines()).hasSize(1);
        assertThat(suggestion.getStatus()).isEqualTo(SupplySuggestionStatus.CONVERTED);
        assertThat(suggestion.getConvertedReferenceType()).isEqualTo("PURCHASE_REQUISITION");
        verify(supplySuggestionRepository).save(suggestion);
    }

    @Test
    void convertFromSuggestion_wrongTypeFails() {
        SupplySuggestion suggestion = suggestion(
                company(UUID.randomUUID()),
                null,
                null,
                item(UUID.randomUUID(), company(UUID.randomUUID())),
                SupplySuggestionType.WORK_ORDER,
                SupplySuggestionStatus.APPROVED);
        when(supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestion.getSupplySuggestionId()))
                .thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.convertFromSuggestion(
                suggestion.getSupplySuggestionId(),
                new PurchaseRequisitionFromSuggestionRequest("PR-001", UUID.randomUUID(), null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
    }

    @Test
    void convertFromSuggestion_statusNotApproved_shouldThrow() {
        SupplySuggestion suggestion = suggestion(
                company(UUID.randomUUID()),
                null,
                null,
                item(UUID.randomUUID(), company(UUID.randomUUID())),
                SupplySuggestionType.PURCHASE_REQUISITION,
                SupplySuggestionStatus.DRAFT);
        when(supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestion.getSupplySuggestionId()))
                .thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.convertFromSuggestion(
                suggestion.getSupplySuggestionId(),
                new PurchaseRequisitionFromSuggestionRequest("PR-001", UUID.randomUUID(), null, null)))
                .isInstanceOf(AppException.class)
                // D7: suggestion status conflict is 409 STATE_CONFLICT, not 422
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    @Test
    void convertFromSuggestion_alreadyConverted_shouldThrow() {
        SupplySuggestion suggestion = suggestion(
                company(UUID.randomUUID()),
                null,
                null,
                item(UUID.randomUUID(), company(UUID.randomUUID())),
                SupplySuggestionType.PURCHASE_REQUISITION,
                SupplySuggestionStatus.CONVERTED);
        when(supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestion.getSupplySuggestionId()))
                .thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.convertFromSuggestion(
                suggestion.getSupplySuggestionId(),
                new PurchaseRequisitionFromSuggestionRequest("PR-001", UUID.randomUUID(), null, null)))
                .isInstanceOf(AppException.class)
                // D7: suggestion status conflict is 409 STATE_CONFLICT, not 422
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    @Test
    void approveDraft_defaultsApprovedQuantityToRequested() {
        PurchaseRequisition requisition = requisition(PurchaseRequisitionStatus.DRAFT);
        PurchaseRequisitionLine line = requisition.getLines().get(0);
        when(purchaseRequisitionRepository.findWithDetailsByPurchaseRequisitionId(requisition.getPurchaseRequisitionId()))
                .thenReturn(Optional.of(requisition));
        when(purchaseRequisitionRepository.save(requisition)).thenReturn(requisition);

        PurchaseRequisitionResponse response = service.approve(
                requisition.getPurchaseRequisitionId(),
                new PurchaseDecisionRequest("Approved", null));

        assertThat(response.status()).isEqualTo(PurchaseRequisitionStatus.APPROVED.name());
        assertThat(line.getApprovedQuantity()).isEqualByComparingTo(line.getRequestedQuantity());
    }

    @Test
    void convertApprovedRequisitionToPurchaseOrder_marksRequisitionConverted() {
        PurchaseRequisition requisition = requisition(PurchaseRequisitionStatus.APPROVED);
        Supplier supplier = requisition.getLines().get(0).getSupplier();
        PurchaseRequisitionConvertToOrderRequest request = new PurchaseRequisitionConvertToOrderRequest(
                "PO-001", null, LocalDate.now(), LocalDate.now().plusDays(7), "Convert");
        when(purchaseRequisitionRepository.findWithDetailsByPurchaseRequisitionId(requisition.getPurchaseRequisitionId()))
                .thenReturn(Optional.of(requisition));
        when(supplierService.findActiveSupplier(supplier.getSupplierId())).thenReturn(supplier);
        when(purchaseOrderService.createFromRequisition(requisition, supplier, request))
                .thenReturn(purchaseOrderResponse(UUID.randomUUID(), requisition, supplier));

        PurchaseOrderResponse response = service.convertToPurchaseOrder(requisition.getPurchaseRequisitionId(), request);

        assertThat(response.purchaseOrderNo()).isEqualTo("PO-001");
        assertThat(requisition.getStatus()).isEqualTo(PurchaseRequisitionStatus.CONVERTED);
        verify(purchaseRequisitionRepository).save(requisition);
    }

    private PurchaseOrderResponse purchaseOrderResponse(UUID orderId,
                                                        PurchaseRequisition requisition,
                                                        Supplier supplier) {
        return new PurchaseOrderResponse(
                orderId,
                requisition.getCompany().getCompanyId(),
                requisition.getCompany().getCode(),
                requisition.getPlant().getPlantId(),
                requisition.getPlant().getCode(),
                requisition.getWarehouse().getWarehouseId(),
                requisition.getWarehouse().getCode(),
                supplier.getSupplierId(),
                supplier.getCode(),
                supplier.getName(),
                "PO-001",
                PurchaseOrderStatus.DRAFT.name(),
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                requisition.getPurchaseRequisitionId(),
                null,
                null,
                null,
                List.of());
    }

    private PurchaseRequisition requisition(PurchaseRequisitionStatus status) {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Supplier supplier = supplier(UUID.randomUUID(), company);
        PurchaseRequisition requisition = PurchaseRequisition.builder()
                .purchaseRequisitionId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .requisitionNo("PR-001")
                .neededByDate(LocalDate.now().plusDays(7))
                .status(status)
                .lines(new ArrayList<>())
                .build();
        requisition.getLines().add(PurchaseRequisitionLine.builder()
                .purchaseRequisitionLineId(UUID.randomUUID())
                .purchaseRequisition(requisition)
                .item(item(UUID.randomUUID(), company))
                .supplier(supplier)
                .requestedQuantity(new BigDecimal("10"))
                .neededByDate(requisition.getNeededByDate())
                .build());
        return requisition;
    }

    private SupplySuggestion suggestion(Company company,
                                        Plant plant,
                                        Warehouse warehouse,
                                        Item item,
                                        SupplySuggestionType type,
                                        SupplySuggestionStatus status) {
        if (plant == null) {
            plant = plant(UUID.randomUUID(), company);
        }
        if (warehouse == null) {
            warehouse = warehouse(UUID.randomUUID(), plant);
        }
        MrpRun run = MrpRun.builder()
                .mrpRunId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .horizonStartDate(LocalDate.now())
                .horizonEndDate(LocalDate.now().plusDays(30))
                .build();
        MrpRequirementLine requirementLine = MrpRequirementLine.builder()
                .mrpRequirementLineId(UUID.randomUUID())
                .mrpRun(run)
                .item(item)
                .warehouse(warehouse)
                .requirementLevel(0)
                .grossRequiredQuantity(new BigDecimal("10"))
                .netRequiredQuantity(new BigDecimal("10"))
                .dueDate(LocalDate.now().plusDays(7))
                .requirementStatus(MrpRequirementStatus.SHORTAGE)
                .build();
        return SupplySuggestion.builder()
                .supplySuggestionId(UUID.randomUUID())
                .mrpRun(run)
                .requirementLine(requirementLine)
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .item(item)
                .suggestionType(type)
                .suggestedQuantity(new BigDecimal("10"))
                .neededByDate(LocalDate.now().plusDays(7))
                .suggestedOrderDate(LocalDate.now())
                .status(status)
                .build();
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
