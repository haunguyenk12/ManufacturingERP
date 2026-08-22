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
import com.erp.manufacturing.module.purchasing.service.query.PurchaseOrderSupplyService;
import com.erp.manufacturing.module.routing.service.RoutingLookupService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MrpCalculationService tests")
class MrpCalculationServiceTest {

    @Mock BomLookupService bomLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;
    @Mock WorkOrderSupplyService workOrderSupplyService;
    @Mock PurchaseOrderSupplyService purchaseOrderSupplyService;
    @Mock RoutingLookupService routingLookupService;

    MrpCalculationService service;

    @BeforeEach
    void setUp() {
        service = new MrpCalculationService(
                bomLookupService,
                inventoryAvailabilityService,
                workOrderSupplyService,
                purchaseOrderSupplyService,
                routingLookupService);
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
        when(routingLookupService.findActiveRoutingSummaries(eq(company.getCompanyId()), itemIdsContaining(product.getItemId())))
                .thenReturn(Map.of(product.getItemId(), routing("RT-PROD", "1")));

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements()).hasSize(2);
        assertThat(result.requirements().get(0).reservedQuantity()).isEqualByComparingTo("7");
        assertThat(result.requirements().get(0).availableQuantity()).isEqualByComparingTo("3");
        assertThat(result.requirements().get(0).netRequiredQuantity()).isEqualByComparingTo("7");
        assertThat(result.suggestions()).extracting(MrpCalculationService.SuggestionDraft::suggestionType)
                .containsExactly(SupplySuggestionType.WORK_ORDER, SupplySuggestionType.PURCHASE_REQUISITION);
        assertThat(result.suggestions()).extracting(MrpCalculationService.SuggestionDraft::exceptionState)
                .containsOnly(SupplySuggestionExceptionState.READY);
        assertThat(result.suggestions().get(0).messages())
                .containsExactly(PlanningMessageCode.MATERIAL_SHORTAGE);
        assertThat(result.requirements().get(1).grossRequiredQuantity()).isEqualByComparingTo("14");
    }

    @Test
    void calculate_makeItemWithoutActiveRouting_blocksTheProposalWithMissingRouting() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item product = item(UUID.randomUUID(), company, "FG", ItemType.FINISHED_GOOD);
        PlanningDemand demand = demand(company, plant, warehouse, product, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), bom(product)));
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(product.getItemId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), qty("0", "0", "0", "0", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        when(routingLookupService.findActiveRoutingSummaries(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of());

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.suggestions()).hasSize(1);
        MrpCalculationService.SuggestionDraft suggestion = result.suggestions().get(0);
        assertThat(suggestion.suggestionType()).isEqualTo(SupplySuggestionType.WORK_ORDER);
        assertThat(suggestion.exceptionState()).isEqualTo(SupplySuggestionExceptionState.BLOCKED);
        assertThat(suggestion.messages()).containsExactly(
                PlanningMessageCode.MATERIAL_SHORTAGE, PlanningMessageCode.MISSING_ROUTING);
        // B78: no ACTIVE routing means there is no snapshot to freeze — null here is the blocked
        // case speaking, not data lost on the way to the proposal.
        assertThat(suggestion.sourceRoutingCode()).isNull();
        assertThat(suggestion.sourceRoutingVersion()).isNull();
    }

    @Test
    void calculate_componentCovered_materialShortageOnlyDescribesTheFinishedGoodAndDoesNotBlockMake() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item product = item(UUID.randomUUID(), company, "FG", ItemType.FINISHED_GOOD);
        Item component = item(UUID.randomUUID(), company, "RM", ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, product, "2", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);
        BomHeader bom = bom(product, line(component, "2", "0"));

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), bom))
                .thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(product.getItemId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), qty("0", "0", "0", "0", "0", 0)));
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(component.getItemId()), anyCollection()))
                .thenReturn(Map.of(component.getItemId(), qty("200", "0", "200", "0", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(
                eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        when(routingLookupService.findActiveRoutingSummaries(
                eq(company.getCompanyId()), itemIdsContaining(product.getItemId())))
                .thenReturn(Map.of(product.getItemId(), routing("WOTEST-RT", "A")));

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements()).hasSize(2);
        assertThat(result.requirements().get(0).status()).isEqualTo(MrpRequirementStatus.SHORTAGE);
        assertThat(result.requirements().get(0).netRequiredQuantity()).isEqualByComparingTo("2");
        assertThat(result.requirements().get(1).status()).isEqualTo(MrpRequirementStatus.COVERED);
        assertThat(result.requirements().get(1).grossRequiredQuantity()).isEqualByComparingTo("4");
        assertThat(result.requirements().get(1).netRequiredQuantity()).isEqualByComparingTo("0");

        assertThat(result.suggestions()).hasSize(1);
        MrpCalculationService.SuggestionDraft make = result.suggestions().get(0);
        assertThat(make.suggestionType()).isEqualTo(SupplySuggestionType.WORK_ORDER);
        assertThat(make.exceptionState()).isEqualTo(SupplySuggestionExceptionState.READY);
        assertThat(make.messages()).containsExactly(PlanningMessageCode.MATERIAL_SHORTAGE);
        assertThat(make.messages()).doesNotContain(PlanningMessageCode.MISSING_ROUTING);
        assertThat(make.sourceRoutingCode()).isEqualTo("WOTEST-RT");
        assertThat(make.sourceRoutingVersion()).isEqualTo("A");
    }

    @Test
    void calculate_manufacturableItemWithoutActiveBom_stillEmitsABlockedMakeProposal() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item product = item(UUID.randomUUID(), company, "FG", ItemType.FINISHED_GOOD);
        PlanningDemand demand = demand(company, plant, warehouse, product, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection())).thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(product.getItemId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), qty("0", "0", "0", "0", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        when(routingLookupService.findActiveRoutingSummaries(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), routing("RT-PROD", "1")));

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements().get(0).status()).isEqualTo(MrpRequirementStatus.BOM_MISSING);
        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).suggestionType()).isEqualTo(SupplySuggestionType.WORK_ORDER);
        assertThat(result.suggestions().get(0).exceptionState()).isEqualTo(SupplySuggestionExceptionState.BLOCKED);
        assertThat(result.suggestions().get(0).messages()).containsExactly(
                PlanningMessageCode.MATERIAL_SHORTAGE, PlanningMessageCode.MISSING_BOM);
    }

    /**
     * F10 / debt G. The whole reason {@code projectedAvailableQuantity} is a column: the second line
     * for the same item sees only what the first line left behind, so a reader deriving
     * {@code availableQuantity + openSupplyQuantity} would report 12 where the netting used 2.
     *
     * <p>Two demands for one item, 12 on hand:
     * line 1 takes 10 of the coverage (projected 12, net 0), line 2 is left with 2 (net 10 - 2 = 8).
     */
    @Test
    void calculate_sameItemOnTwoRequirementLines_reportsTheCoverageLeftForTheSecondLineNotTheGrossAvailable() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item raw = item(UUID.randomUUID(), company, "RM", ItemType.RAW_MATERIAL);
        PlanningDemand first = demand(company, plant, warehouse, raw, "10", LocalDate.now().plusDays(10));
        PlanningDemand second = demand(company, plant, warehouse, raw, "10", LocalDate.now().plusDays(20));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection())).thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(raw.getItemId()), anyCollection()))
                .thenReturn(Map.of(raw.getItemId(), qty("12", "0", "12", "0", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(first, second), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements()).hasSize(2);
        MrpCalculationService.RequirementDraft line1 = result.requirements().get(0);
        MrpCalculationService.RequirementDraft line2 = result.requirements().get(1);

        assertThat(line1.projectedAvailableQuantity()).isEqualByComparingTo("12");
        assertThat(line1.netRequiredQuantity()).isEqualByComparingTo("0");

        assertThat(line2.projectedAvailableQuantity()).isEqualByComparingTo("2");
        assertThat(line2.netRequiredQuantity()).isEqualByComparingTo("8");
        // Both lines still report the same raw stock figures — which is exactly why deriving
        // projectedAvailable from them client-side gives the wrong answer for line 2.
        assertThat(line2.availableQuantity()).isEqualByComparingTo("12");
        assertThat(line2.openSupplyQuantity()).isEqualByComparingTo("0");
    }

    /**
     * F10 / invariant B77: the persisted figure has to be the one the netting used, clamp included,
     * so the frontend can reproduce {@code net = max(0, gross + safetyStock - projectedAvailable)}.
     * Here 3 on hand against a demand of 10 with a safety stock of 5: net = 10 + 5 - 3 = 12.
     */
    @Test
    void calculate_projectedAvailableIsTheFigureTheNettingUsed() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item raw = item(UUID.randomUUID(), company, "RM", ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, raw, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection())).thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(raw.getItemId()), anyCollection()))
                .thenReturn(Map.of(raw.getItemId(), qty("3", "0", "3", "5", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        MrpCalculationService.RequirementDraft line = result.requirements().get(0);
        assertThat(line.projectedAvailableQuantity()).isEqualByComparingTo("3");
        assertThat(line.safetyStockQuantity()).isEqualByComparingTo("5");
        assertThat(line.netRequiredQuantity()).isEqualByComparingTo("12");
        assertThat(line.grossRequiredQuantity()
                .add(line.safetyStockQuantity())
                .subtract(line.projectedAvailableQuantity()))
                .isEqualByComparingTo(line.netRequiredQuantity());
    }

    /**
     * F10 / debt F, invariant B78. A MAKE proposal freezes the routing that was ACTIVE when the run
     * executed (spec §2.4); the BUY proposal for its component carries none, because a purchase has
     * no routing at all.
     */
    @Test
    void calculate_makeProposal_freezesTheActiveRoutingCodeAndVersion_buyProposalCarriesNone() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item product = item(UUID.randomUUID(), company, "FG", ItemType.FINISHED_GOOD);
        Item component = item(UUID.randomUUID(), company, "RM", ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, product, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), bom(product, line(component, "1", "0"))))
                .thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(product.getItemId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), qty("0", "0", "0", "0", "0", 0)));
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(component.getItemId()), anyCollection()))
                .thenReturn(Map.of(component.getItemId(), qty("0", "0", "0", "0", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        // The component is given a routing on purpose, even though a purchased item would not have
        // one in practice: without it the "BUY carries none" assertion below passes for the wrong
        // reason (no routing to carry) and stops guarding the supplyType check at all.
        when(routingLookupService.findActiveRoutingSummaries(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(
                        product.getItemId(), routing("RT-ASSY", "2"),
                        component.getItemId(), routing("RT-BOLT", "9")));

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        MrpCalculationService.SuggestionDraft make = result.suggestions().get(0);
        assertThat(make.suggestionType()).isEqualTo(SupplySuggestionType.WORK_ORDER);
        assertThat(make.sourceRoutingCode()).isEqualTo("RT-ASSY");
        assertThat(make.sourceRoutingVersion()).isEqualTo("2");

        MrpCalculationService.SuggestionDraft buy = result.suggestions().get(1);
        assertThat(buy.suggestionType()).isEqualTo(SupplySuggestionType.PURCHASE_REQUISITION);
        assertThat(buy.sourceRoutingCode()).isNull();
        assertThat(buy.sourceRoutingVersion()).isNull();
    }

    @Test
    void calculate_noItemWarehouseSetting_recordsSystemDefaultSourceAndWarns() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item raw = item(UUID.randomUUID(), company, "RM", ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, raw, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection())).thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(raw.getItemId()), anyCollection()))
                .thenReturn(Map.of(raw.getItemId(), qty("0", "0", "0", "0", "0", 0, false, 3)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements().get(0).settingSource()).isEqualTo(PlanningSettingSource.SYSTEM_DEFAULT);
        assertThat(result.requirements().get(0).excludedLotCount()).isEqualTo(3);
        assertThat(result.suggestions().get(0).exceptionState()).isEqualTo(SupplySuggestionExceptionState.WARNING);
        assertThat(result.suggestions().get(0).messages()).containsExactly(
                PlanningMessageCode.MATERIAL_SHORTAGE,
                PlanningMessageCode.SYSTEM_FALLBACK_USED,
                PlanningMessageCode.PURCHASING_DEFERRED);
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

    /**
     * D4 / debt #16: locks the whole netting formula of spec §2.1 with real numbers, including both
     * halves of {@code scheduledReceipts}. Before D4 the purchase-order half was simply missing, so
     * this line would have netted to 10 and proposed buying material already on order.
     */
    @Test
    void calculate_openPurchaseOrderSupplyIsNettedTogetherWithOpenWorkOrderSupply() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item raw = item(UUID.randomUUID(), company, "RM", ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, raw, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection())).thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(raw.getItemId()), anyCollection()))
                .thenReturn(Map.of(raw.getItemId(), qty("2", "0", "2", "5", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), itemIdsContaining(raw.getItemId())))
                .thenReturn(Map.of(raw.getItemId(), new BigDecimal("3")));
        when(purchaseOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), itemIdsContaining(raw.getItemId())))
                .thenReturn(Map.of(raw.getItemId(), new BigDecimal("4")));

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        MrpCalculationService.RequirementDraft requirement = result.requirements().get(0);
        assertThat(requirement.openSupplyQuantity()).isEqualByComparingTo("7");   // 3 open WO + 4 open PO
        assertThat(requirement.netRequiredQuantity()).isEqualByComparingTo("6");  // (10 + 5) − (2 + 7)
        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).suggestionType())
                .isEqualTo(SupplySuggestionType.PURCHASE_REQUISITION);
    }

    /**
     * The point of debt #16: material already ordered must not be ordered twice. This is the case
     * that fails loudest if the purchase-order term is ever dropped from netting again.
     */
    @Test
    void calculate_openPurchaseOrderCoversDemand_emitsNoPurchaseProposal() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item raw = item(UUID.randomUUID(), company, "RM", ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, raw, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection())).thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(raw.getItemId()), anyCollection()))
                .thenReturn(Map.of(raw.getItemId(), qty("0", "0", "0", "0", "0", 0)));
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        when(purchaseOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), itemIdsContaining(raw.getItemId())))
                .thenReturn(Map.of(raw.getItemId(), new BigDecimal("10")));

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements().get(0).status()).isEqualTo(MrpRequirementStatus.COVERED);
        assertThat(result.requirements().get(0).netRequiredQuantity()).isEqualByComparingTo("0");
        assertThat(result.suggestions()).isEmpty();
    }

    /**
     * Rule {@code C15}: both supply services are read once per BOM level, never once per seed. The
     * BOM here has two components so a per-seed regression shows up as 3 calls instead of 2 — with a
     * single component the two shapes would be indistinguishable.
     */
    @Test
    void calculate_readsEachSupplyServiceOncePerBomLevel_notOncePerSeed() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item product = item(UUID.randomUUID(), company, "FG", ItemType.FINISHED_GOOD);
        Item firstComponent = item(UUID.randomUUID(), company, "RM1", ItemType.RAW_MATERIAL);
        Item secondComponent = item(UUID.randomUUID(), company, "RM2", ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, product, "10", LocalDate.now().plusDays(10));
        MrpRun run = run(company, plant, warehouse);

        when(bomLookupService.findActiveBoms(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(),
                        bom(product, line(firstComponent, "1", "0"), line(secondComponent, "2", "0"))))
                .thenReturn(Map.of());
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(product.getItemId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), qty("0", "0", "0", "0", "0", 0)));
        when(inventoryAvailabilityService.getPlanningQuantities(itemIdsContaining(firstComponent.getItemId()), anyCollection()))
                .thenReturn(Map.of());
        when(workOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        when(purchaseOrderSupplyService.getOpenSupplyQuantities(eq(company.getCompanyId()), eq(plant.getPlantId()), anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        when(routingLookupService.findActiveRoutingSummaries(eq(company.getCompanyId()), anyCollection()))
                .thenReturn(Map.of(product.getItemId(), routing("RT-PROD", "1")));

        MrpCalculationService.MrpCalculationResult result =
                service.calculate(run, List.of(demand), List.of(warehouse.getWarehouseId()));

        assertThat(result.requirements()).hasSize(3);
        verify(workOrderSupplyService, times(2))
                .getOpenSupplyQuantities(any(), any(), anyCollection(), anyCollection());
        verify(purchaseOrderSupplyService, times(2))
                .getOpenSupplyQuantities(any(), any(), anyCollection(), anyCollection());
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

    private RoutingLookupService.RoutingSummary routing(String code, String version) {
        return new RoutingLookupService.RoutingSummary(UUID.randomUUID(), code, version);
    }

    private PlanningInventoryQuantity qty(String onHand,
                                          String reserved,
                                          String available,
                                          String safety,
                                          String reorder,
                                          int leadTimeDays) {
        return qty(onHand, reserved, available, safety, reorder, leadTimeDays, true, 0);
    }

    private PlanningInventoryQuantity qty(String onHand,
                                          String reserved,
                                          String available,
                                          String safety,
                                          String reorder,
                                          int leadTimeDays,
                                          boolean hasItemWarehouseSetting,
                                          int excludedLotCount) {
        return new PlanningInventoryQuantity(
                new BigDecimal(onHand),
                new BigDecimal(reserved),
                new BigDecimal(available),
                new BigDecimal(safety),
                new BigDecimal(reorder),
                leadTimeDays,
                hasItemWarehouseSetting,
                excludedLotCount);
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
