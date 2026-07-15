package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.dto.MrpRunCreateRequest;
import com.erp.manufacturing.module.planning.dto.MrpRunResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MrpRunService tests")
class MrpRunServiceTest {

    @Mock MrpRunRepository mrpRunRepository;
    @Mock PlanningDemandRepository planningDemandRepository;
    @Mock MrpRunDemandRepository mrpRunDemandRepository;
    @Mock MrpRequirementLineRepository requirementLineRepository;
    @Mock SupplySuggestionRepository supplySuggestionRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock MrpCalculationService calculationService;
    @Mock AuditLogService auditLogService;

    MrpRunService service;

    @BeforeEach
    void setUp() {
        service = new MrpRunService(
                mrpRunRepository,
                planningDemandRepository,
                mrpRunDemandRepository,
                requirementLineRepository,
                supplySuggestionRepository,
                organizationLookupService,
                calculationService,
                new MrpPlanningMapper(),
                auditLogService);
    }

    @Test
    void run_snapshotsDemandAndPersistsCalculationOutput() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item item = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, item);
        MrpCalculationService.RequirementDraft requirement = new MrpCalculationService.RequirementDraft(
                null,
                demand,
                item,
                warehouse,
                0,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("10"),
                demand.getDueDate(),
                MrpRequirementStatus.SHORTAGE,
                null,
                demand.getDueDate());

        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(organizationLookupService.getActiveWarehouse(warehouse.getWarehouseId())).thenReturn(warehouse);
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            if (run.getMrpRunId() == null) {
                run.setMrpRunId(UUID.randomUUID());
            }
            return run;
        });
        when(planningDemandRepository.findOpenDemandsForRun(
                company.getCompanyId(),
                plant.getPlantId(),
                warehouse.getWarehouseId(),
                LocalDate.now(),
                LocalDate.now().plusDays(30))).thenReturn(List.of(demand));
        when(calculationService.calculate(any(MrpRun.class), eq(List.of(demand)), eq(List.of(warehouse.getWarehouseId()))))
                .thenReturn(new MrpCalculationService.MrpCalculationResult(
                        List.of(requirement),
                        List.of(new MrpCalculationService.SuggestionDraft(
                                requirement, SupplySuggestionType.PURCHASE_REQUISITION))));
        when(requirementLineRepository.save(any(MrpRequirementLine.class))).thenAnswer(invocation -> {
            MrpRequirementLine line = invocation.getArgument(0);
            line.setMrpRequirementLineId(UUID.randomUUID());
            return line;
        });

        MrpRunResponse response = service.run(new MrpRunCreateRequest(
                company.getCompanyId(),
                plant.getPlantId(),
                warehouse.getWarehouseId(),
                LocalDate.now(),
                LocalDate.now().plusDays(30)));

        assertThat(response.status()).isEqualTo(MrpRunStatus.COMPLETED.name());
        assertThat(response.totalDemandLines()).isEqualTo(1);
        assertThat(response.totalRequirementLines()).isEqualTo(1);
        assertThat(response.totalSuggestionLines()).isEqualTo(1);
        verify(mrpRunDemandRepository).saveAll(argThat(snapshots -> snapshots.iterator().hasNext()));
        verify(supplySuggestionRepository).saveAll(argThat(suggestions -> suggestions.iterator().hasNext()));
    }

    private PlanningDemand demand(Company company, Plant plant, Warehouse warehouse, Item item) {
        return PlanningDemand.builder()
                .planningDemandId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .item(item)
                .demandType(PlanningDemandType.MANUAL)
                .requiredQuantity(new BigDecimal("10"))
                .dueDate(LocalDate.now().plusDays(10))
                .priority(10)
                .status(PlanningDemandStatus.OPEN)
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
