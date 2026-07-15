package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService.PlanningInventoryQuantity;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderSupplyService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MrpCalculationService tests")
class MrpCalculationServiceTest {

    @Mock BomLookupService bomLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;
    @Mock WorkOrderSupplyService workOrderSupplyService;

    MrpCalculationService service;

    @BeforeEach
    void setUp() {
        service = new MrpCalculationService(
                bomLookupService,
                inventoryAvailabilityService,
                workOrderSupplyService);
    }

    @Test
    void calculate_availableStockCoversDemand_noSuggestion() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item product = item(UUID.randomUUID(), company, "FG", ItemType.FINISHED_GOOD);
        PlanningDemand demand = demand(company, plant, warehouse, product, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), bom(product)));
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(product.getItemId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), qty("15", "0", "15", "0", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), itemIdsContaining(product.getItemId())))
                .thenReturn(Map.of());

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements()).hasSize(1);
        assertThat(result.requirements().get(0).status()).isEqualTo(MrpRequirementStatus.COVERED);
        assertThat(result.requirements().get(0).netRequiredQuantity()).isEqualByComparingTo("0");
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void calculate_reservedStockIsNotAvailable_createsWorkOrderSuggestionAndExplodesComponents() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item product = item(UUID.randomUUID(), company, "FG", ItemType.FINISHED_GOOD);
        Item component = item(UUID.randomUUID(), company, "RM", ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, product, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);
        BomHeader bom = bom(product, line(component, "2", "0"));

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), bom))
                .thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(product.getItemId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), qty("10", "7", "3", "0", "0", 0)));
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(component.getItemId()), anyCollection()))
                .thenReturn(Map.of(component.getItemId(), qty("0", "0", "0", "0", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements()).hasSize(2);
        assertThat(result.requirements().get(0).reservedQuantity()).isEqualByComparingTo("7");
        assertThat(result.requirements().get(0).availableQuantity()).isEqualByComparingTo("3");
        assertThat(result.requirements().get(0).netRequiredQuantity()).isEqualByComparingTo("7");
        assertThat(result.suggestions()).extracting(MrpCalculationService.SuggestionDraft::suggestionType)
                .containsExactly(SupplySuggestionType.WORK_ORDER, SupplySuggestionType.PURCHASE_REQUISITION);
        assertThat(result.requirements().get(1).grossRequiredQuantity()).isEqualByComparingTo("14");
    }

    @Test
    void calculate_openWorkOrderSupplyReducesNetRequirement() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item product = item(UUID.randomUUID(), company, "FG", ItemType.FINISHED_GOOD);
        PlanningDemand demand = demand(company, plant, warehouse, product, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), bom(product)));
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(product.getItemId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), qty("2", "0", "2", "0", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), itemIdsContaining(product.getItemId())))
                .thenReturn(Map.of(product.getItemId(), new BigDecimal("8")));

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements().get(0).openSupplyQuantity()).isEqualByComparingTo("8");
        assertThat(result.requirements().get(0).netRequiredQuantity()).isEqualByComparingTo("0");
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void calculate_safetyStockAndLeadTimeDrivePurchaseSuggestion() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item raw = item(UUID.randomUUID(), company, "RM", ItemType.RAW_MATERIAL);
        LocalDate dueDate = LocalDate.now().plusDays(12);
        PlanningDemand demand = demand(company, plant, warehouse, raw, "10", dueDate);
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection())).thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(raw.getItemId()), anyCollection()))
                .thenReturn(Map.of(raw.getItemId(), qty("8", "0", "8", "5", "2", 3)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), itemIdsContaining(raw.getItemId())))
                .thenReturn(Map.of());

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements().get(0).safetyStockQuantity()).isEqualByComparingTo("5");
        assertThat(result.requirements().get(0).netRequiredQuantity()).isEqualByComparingTo("7");
        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).suggestionType()).isEqualTo(SupplySuggestionType.PURCHASE_REQUISITION);
        assertThat(result.suggestions().get(0).requirement().suggestedOrderDate()).isEqualTo(dueDate.minusDays(3));
    }

    private MrpRun run(Company company, Plant plant, Warehouse warehouse) {
        return MrpRun.builder()
                .mrpRunId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .horizonStartDate(LocalDate.now())
                .horizonEndDate(LocalDate.now().plusDays(30))
                .build();
    }

    private java.util.Collection<UUID> itemIdsContaining(UUID itemId) {
        return org.mockito.ArgumentMatchers.argThat(ids -> ids != null && ids.contains(itemId));
    }

    private PlanningDemand demand(Company company, Plant plant, Warehouse warehouse, Item item, String quantity, LocalDate dueDate) {
        return PlanningDemand.builder()
                .planningDemandId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .item(item)
                .demandType(PlanningDemandType.MANUAL)
                .requiredQuantity(new BigDecimal(quantity))
                .dueDate(dueDate)
                .status(PlanningDemandStatus.OPEN)
                .build();
    }

    private PlanningInventoryQuantity qty(String onHand,
                                          String reserved,
                                          String available,
                                          String safety,
                                          String reorder,
                                          int leadTimeDays) {
        return new PlanningInventoryQuantity(
                new BigDecimal(onHand),
                new BigDecimal(reserved),
                new BigDecimal(available),
                new BigDecimal(safety),
                new BigDecimal(reorder),
                leadTimeDays);
    }

    private BomHeader bom(Item parent, BomLine... lines) {
        BomHeader bom = BomHeader.builder()
                .bomId(UUID.randomUUID())
                .company(parent.getCompany())
                .parentItem(parent)
                .revision("R1")
                .status(BomStatus.ACTIVE)
                .lines(new ArrayList<>())
                .build();
        for (BomLine line : lines) {
            line.setBom(bom);
            bom.getLines().add(line);
        }
        return bom;
    }

    private BomLine line(Item component, String quantityPer, String scrapRate) {
        return BomLine.builder()
                .lineId(UUID.randomUUID())
                .componentItem(component)
                .lineNo(10)
                .quantityPer(new BigDecimal(quantityPer))
                .scrapRate(new BigDecimal(scrapRate))
                .build();
    }

    private Item item(UUID itemId, Company company, String code, ItemType type) {
        return Item.builder()
                .itemId(itemId)
                .company(company)
                .code(code)
                .name(code)
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
