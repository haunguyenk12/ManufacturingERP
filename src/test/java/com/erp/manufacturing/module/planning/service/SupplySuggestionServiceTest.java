package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.dto.SupplySuggestionConvertWorkOrderRequest;
import com.erp.manufacturing.module.planning.dto.SupplySuggestionDecisionRequest;
import com.erp.manufacturing.module.planning.dto.SupplySuggestionResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionRepository;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderCreateRequest;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderResponse;
import com.erp.manufacturing.module.workorder.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplySuggestionService tests")
class SupplySuggestionServiceTest {

    @Mock SupplySuggestionRepository supplySuggestionRepository;
    @Mock WorkOrderService workOrderService;

    SupplySuggestionService service;

    @BeforeEach
    void setUp() {
        service = new SupplySuggestionService(
                supplySuggestionRepository,
                workOrderService,
                new MrpPlanningMapper());
    }

    @Test
    void approve_draftSuggestion_success() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.PURCHASE_REQUISITION, SupplySuggestionStatus.DRAFT);
        when(supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestion.getSupplySuggestionId()))
                .thenReturn(Optional.of(suggestion));
        when(supplySuggestionRepository.save(suggestion)).thenReturn(suggestion);

        SupplySuggestionResponse response = service.approve(
                suggestion.getSupplySuggestionId(),
                new SupplySuggestionDecisionRequest("Looks good"));

        assertThat(response.status()).isEqualTo(SupplySuggestionStatus.APPROVED.name());
        assertThat(response.decisionNote()).isEqualTo("Looks good");
    }

    @Test
    void reject_convertedSuggestion_fails() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.PURCHASE_REQUISITION, SupplySuggestionStatus.CONVERTED);
        when(supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestion.getSupplySuggestionId()))
                .thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.reject(
                suggestion.getSupplySuggestionId(),
                new SupplySuggestionDecisionRequest("No")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(supplySuggestionRepository, never()).save(any());
    }

    @Test
    void convertToWorkOrder_approvedWorkOrderSuggestion_createsWorkOrderAndMarksConverted() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.WORK_ORDER, SupplySuggestionStatus.APPROVED);
        UUID workOrderId = UUID.randomUUID();
        when(supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestion.getSupplySuggestionId()))
                .thenReturn(Optional.of(suggestion));
        when(workOrderService.createFromMrp(eq(suggestion.getPlant().getPlantId()), any(WorkOrderCreateRequest.class)))
                .thenReturn(workOrderResponse(workOrderId, suggestion));
        when(supplySuggestionRepository.save(suggestion)).thenReturn(suggestion);

        SupplySuggestionResponse response = service.convertToWorkOrder(
                suggestion.getSupplySuggestionId(),
                new SupplySuggestionConvertWorkOrderRequest(
                        "WO-MRP-001",
                        null,
                        null,
                        null,
                        "Create from MRP"));

        assertThat(response.status()).isEqualTo(SupplySuggestionStatus.CONVERTED.name());
        assertThat(response.convertedReferenceType()).isEqualTo("WORK_ORDER");
        assertThat(response.convertedReferenceId()).isEqualTo(workOrderId);
        verify(workOrderService).createFromMrp(eq(suggestion.getPlant().getPlantId()), argThat(request ->
                request.workOrderNo().equals("WO-MRP-001")
                        && request.productItemId().equals(suggestion.getItem().getItemId())
                        && request.outputWarehouseId().equals(suggestion.getWarehouse().getWarehouseId())
                        && request.plannedQuantity().compareTo(suggestion.getSuggestedQuantity()) == 0));
    }

    @Test
    void convertToWorkOrder_purchaseSuggestion_fails() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.PURCHASE_REQUISITION, SupplySuggestionStatus.APPROVED);
        when(supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestion.getSupplySuggestionId()))
                .thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.convertToWorkOrder(
                suggestion.getSupplySuggestionId(),
                new SupplySuggestionConvertWorkOrderRequest(null, null, null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verifyNoInteractions(workOrderService);
    }

    private WorkOrderResponse workOrderResponse(UUID workOrderId, SupplySuggestion suggestion) {
        return new WorkOrderResponse(
                workOrderId,
                suggestion.getCompany().getCompanyId(),
                suggestion.getPlant().getPlantId(),
                suggestion.getPlant().getCode(),
                "WO-MRP-001",
                suggestion.getItem().getItemId(),
                suggestion.getItem().getCode(),
                suggestion.getItem().getName(),
                UUID.randomUUID(),
                "R1",
                suggestion.getWarehouse().getWarehouseId(),
                suggestion.getWarehouse().getCode(),
                suggestion.getSuggestedQuantity(),
                BigDecimal.ZERO,
                suggestion.getSuggestedQuantity(),
                "DRAFT",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of());
    }

    private SupplySuggestion suggestion(SupplySuggestionType type, SupplySuggestionStatus status) {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item item = item(UUID.randomUUID(), company, type == SupplySuggestionType.WORK_ORDER
                ? ItemType.FINISHED_GOOD
                : ItemType.RAW_MATERIAL);
        MrpRun run = MrpRun.builder()
                .mrpRunId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .horizonStartDate(LocalDate.now())
                .horizonEndDate(LocalDate.now().plusDays(30))
                .status(MrpRunStatus.COMPLETED)
                .build();
        MrpRequirementLine requirementLine = MrpRequirementLine.builder()
                .mrpRequirementLineId(UUID.randomUUID())
                .mrpRun(run)
                .item(item)
                .warehouse(warehouse)
                .requirementLevel(0)
                .grossRequiredQuantity(new BigDecimal("10"))
                .netRequiredQuantity(new BigDecimal("10"))
                .dueDate(LocalDate.now().plusDays(10))
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
                .neededByDate(LocalDate.now().plusDays(10))
                .suggestedOrderDate(LocalDate.now().plusDays(7))
                .status(status)
                .build();
    }

    private Item item(UUID itemId, Company company, ItemType type) {
        return Item.builder()
                .itemId(itemId)
                .company(company)
                .code("ITEM")
                .name("Item")
                .type(type)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
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
