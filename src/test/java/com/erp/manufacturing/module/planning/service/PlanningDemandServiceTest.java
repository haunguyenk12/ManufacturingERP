package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.domain.PlanningDemand;
import com.erp.manufacturing.module.planning.domain.PlanningDemandStatus;
import com.erp.manufacturing.module.planning.domain.PlanningDemandType;
import com.erp.manufacturing.module.planning.dto.PlanningDemandCreateRequest;
import com.erp.manufacturing.module.planning.dto.PlanningDemandResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.PlanningDemandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanningDemandService tests")
class PlanningDemandServiceTest {

    @Mock PlanningDemandRepository planningDemandRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemLookupService itemLookupService;

    PlanningDemandService service;

    @BeforeEach
    void setUp() {
        service = new PlanningDemandService(
                planningDemandRepository,
                organizationLookupService,
                itemLookupService,
                new MrpPlanningMapper());
    }

    @Test
    void create_manualDemand_success() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Company company = company(companyId);
        Plant plant = plant(plantId, company);
        Item item = item(itemId, company, ItemType.FINISHED_GOOD);
        Warehouse warehouse = warehouse(warehouseId, plant);
        PlanningDemandCreateRequest request = new PlanningDemandCreateRequest(
                companyId,
                plantId,
                itemId,
                warehouseId,
                null,
                new BigDecimal("25"),
                LocalDate.now().plusDays(7),
                null,
                "MANUAL",
                "REQ-1");

        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant);
        when(organizationLookupService.getActiveWarehouse(warehouseId)).thenReturn(warehouse);
        when(itemLookupService.getActiveItem(itemId)).thenReturn(item);
        when(planningDemandRepository.save(any(PlanningDemand.class))).thenAnswer(invocation -> {
            PlanningDemand demand = invocation.getArgument(0);
            demand.setPlanningDemandId(UUID.randomUUID());
            return demand;
        });

        PlanningDemandResponse response = service.create(request);

        assertThat(response.demandType()).isEqualTo(PlanningDemandType.MANUAL.name());
        assertThat(response.status()).isEqualTo(PlanningDemandStatus.OPEN.name());
        assertThat(response.requiredQuantity()).isEqualByComparingTo("25");
        assertThat(response.priority()).isEqualTo(100);
        verify(planningDemandRepository).save(any(PlanningDemand.class));
    }

    @Test
    void create_nonPositiveQuantity_fails() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Company company = company(companyId);
        Plant plant = plant(plantId, company);
        Item item = item(itemId, company, ItemType.FINISHED_GOOD);

        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant);
        when(itemLookupService.getActiveItem(itemId)).thenReturn(item);

        assertThatThrownBy(() -> service.create(new PlanningDemandCreateRequest(
                companyId,
                plantId,
                itemId,
                null,
                PlanningDemandType.MANUAL,
                BigDecimal.ZERO,
                LocalDate.now().plusDays(1),
                10,
                null,
                null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.NEGATIVE_QUANTITY));

        verify(planningDemandRepository, never()).save(any());
    }

    @Test
    void cancel_nonOpenDemand_fails() {
        UUID demandId = UUID.randomUUID();
        PlanningDemand demand = demand(demandId, PlanningDemandStatus.CANCELLED);
        when(planningDemandRepository.findWithDetailsByPlanningDemandId(demandId)).thenReturn(Optional.of(demand));

        assertThatThrownBy(() -> service.cancel(demandId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(planningDemandRepository, never()).save(any());
    }

    private PlanningDemand demand(UUID demandId, PlanningDemandStatus status) {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        return PlanningDemand.builder()
                .planningDemandId(demandId)
                .company(company)
                .plant(plant)
                .item(item(UUID.randomUUID(), company, ItemType.FINISHED_GOOD))
                .demandType(PlanningDemandType.MANUAL)
                .requiredQuantity(BigDecimal.ONE)
                .dueDate(LocalDate.now().plusDays(1))
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
                .type(WarehouseType.FINISHED_GOODS)
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
