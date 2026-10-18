package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.dto.CommercialPlanningRunRequest;
import com.erp.manufacturing.module.planning.dto.ItemWarehouseMappingRequest;
import com.erp.manufacturing.module.planning.dto.MrpRequirementLineResponse;
import com.erp.manufacturing.module.planning.dto.MrpRunResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.*;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSource;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MrpRunService commercial run tests (G6b)")
class CommercialPlanningRunServiceTest {

    @Mock MrpRunRepository mrpRunRepository;
    @Mock PlanningDemandRepository planningDemandRepository;
    @Mock MrpRunDemandRepository mrpRunDemandRepository;
    @Mock MrpRequirementLineRepository requirementLineRepository;
    @Mock SupplySuggestionRepository supplySuggestionRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock MrpCalculationService calculationService;
    @Mock AuditLogService auditLogService;
    @Mock MrpRunStateRecorder runStateRecorder;
    @Mock SalesOrderPlanningSourceService salesOrderPlanningSourceService;
    @Mock PlanningSourceVersionCalculator sourceVersionCalculator;
    @Mock SupplySuggestionConversionRepository conversionRepository;
    @Mock ItemLookupService itemLookupService;

    MrpRunService service;
    Company company;
    Plant plant;
    Warehouse warehouse;
    Item product;
    UUID lineId;

    @BeforeEach
    void setUp() {
        service = new MrpRunService(mrpRunRepository, planningDemandRepository, mrpRunDemandRepository,
                requirementLineRepository, supplySuggestionRepository, organizationLookupService, calculationService,
                new MrpPlanningMapper(), auditLogService, new IdempotencySupport(new ObjectMapper().findAndRegisterModules()),
                runStateRecorder, salesOrderPlanningSourceService, sourceVersionCalculator, conversionRepository,
                itemLookupService);
        company = Company.builder().companyId(UUID.randomUUID()).code("C").name("C").status(OrganizationStatus.ACTIVE).build();
        plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").name("P").status(OrganizationStatus.ACTIVE).build();
        warehouse = Warehouse.builder().warehouseId(UUID.randomUUID()).plant(plant).code("W").name("W")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build();
        product = Item.builder().itemId(UUID.randomUUID()).company(company).code("FG").name("FG")
                .type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).build();
        lineId = UUID.randomUUID();
    }

    /** A15: 1 and 90 days are valid, 91 is refused before anything is read. */
    @Test
    void runCommercial_ninetyOneDayPeriod_isAFieldErrorBeforeAnyLookup_A15() {
        assertThatThrownBy(() -> service.runCommercial(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1), null, null), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("horizonEndDate");
                });
        verifyNoInteractions(organizationLookupService, salesOrderPlanningSourceService, runStateRecorder);
    }

    @Test
    void runCommercial_includeRop_isRefusedBecauseNoReorderQuantityExists_A25() {
        assertThatThrownBy(() -> service.runCommercial(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), null, true), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
        verifyNoInteractions(runStateRecorder);
    }

    @Test
    void runCommercial_lineOfAnotherPlant_isNotFoundBeforeTheRunIsCreated() {
        stubOrganization();
        when(salesOrderPlanningSourceService.findSources(List.of(lineId)))
                .thenReturn(Map.of(lineId, source(UUID.randomUUID(), true, "70")));

        assertThatThrownBy(() -> service.runCommercial(validRequest(null), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
        verifyNoInteractions(runStateRecorder);
    }

    @Test
    void runCommercial_lineWithNothingUncovered_isStateConflict_A14() {
        stubOrganization();
        when(salesOrderPlanningSourceService.findSources(List.of(lineId)))
                .thenReturn(Map.of(lineId, source(plant.getPlantId(), true, "0")));

        assertThatThrownBy(() -> service.runCommercial(validRequest(null), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        verifyNoInteractions(runStateRecorder);
    }

    @Test
    void runCommercial_outputWarehouseDifferentFromNetting_isAFieldError_Q16() {
        stubOrganization();
        ItemWarehouseMappingRequest mapping = new ItemWarehouseMappingRequest(
                product.getItemId(), warehouse.getWarehouseId(), UUID.randomUUID());

        assertThatThrownBy(() -> service.runCommercial(validRequest(List.of(mapping)), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors())
                        .containsKey("itemWarehouseMappings[0].outputWarehouseId"));
        verifyNoInteractions(salesOrderPlanningSourceService, runStateRecorder);
    }

    /**
     * A16: the run plans the uncovered 70 of an order of 100 with 30 held — the demand snapshot and
     * the top-level quantity are 70, the item is netted in the mapped warehouse, and the source
     * version is recorded on the run.
     */
    @Test
    void runCommercial_plansTheUncoveredQuantityInTheMappedWarehouseAndRecordsTheSourceVersion_A16() {
        stubOrganization();
        when(itemLookupService.getActiveItem(product.getItemId())).thenReturn(product);
        when(organizationLookupService.getActiveWarehouseInPlant(warehouse.getWarehouseId(), plant.getPlantId()))
                .thenReturn(warehouse);
        when(organizationLookupService.resolveScope(ScopeResourceType.PLANT, plant.getPlantId()))
                .thenReturn(new OrganizationScopeResolution(ScopeResourceType.PLANT, plant.getPlantId(),
                        company.getCompanyId(), List.of(warehouse.getWarehouseId())));
        when(salesOrderPlanningSourceService.findSources(List.of(lineId)))
                .thenReturn(Map.of(lineId, source(plant.getPlantId(), true, "70")));
        PlanningDemand demand = PlanningDemand.builder()
                .planningDemandId(UUID.randomUUID()).company(company).plant(plant).item(product)
                .demandType(PlanningDemandType.SALES_ORDER).requiredQuantity(new BigDecimal("100"))
                .dueDate(LocalDate.of(2026, 1, 20)).status(PlanningDemandStatus.OPEN)
                .referenceType(PlanningDemandService.REFERENCE_TYPE_SALES_ORDER_LINE).referenceId(lineId.toString())
                .build();
        when(planningDemandRepository.findOpenByReference(eq("SALES_ORDER_LINE"), eq(List.of(lineId.toString()))))
                .thenReturn(List.of(demand));
        when(planningDemandRepository.findSelectedDemandsForRun(List.of(demand.getPlanningDemandId())))
                .thenReturn(List.of(demand));
        stubRunCreation();
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(calculationService.calculate(any(MrpRun.class), anyList(), anyCollection(), anyMap()))
                .thenReturn(new MrpCalculationService.MrpCalculationResult(List.of(), List.of()));
        when(sourceVersionCalculator.compute(eq(company.getCompanyId()), eq(List.of(lineId)), anyCollection()))
                .thenReturn("sha-1");

        MrpRunResponse response = service.runCommercial(
                validRequest(List.of(new ItemWarehouseMappingRequest(product.getItemId(), warehouse.getWarehouseId(), null))),
                null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MrpCalculationService.TopLevelDemand>> topLevels = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<UUID, Warehouse>> mappings = ArgumentCaptor.forClass(Map.class);
        verify(calculationService).calculate(any(MrpRun.class), topLevels.capture(), anyCollection(), mappings.capture());
        assertThat(topLevels.getValue()).singleElement()
                .satisfies(top -> assertThat(top.quantity()).isEqualByComparingTo("70"));
        assertThat(mappings.getValue()).containsEntry(product.getItemId(), warehouse);
        verify(mrpRunDemandRepository).saveAll(argThat(rows -> {
            MrpRunDemand row = rows.iterator().next();
            return row.getRequiredQuantity().compareTo(new BigDecimal("70")) == 0;
        }));
        assertThat(response.runKind()).isEqualTo("COMMERCIAL");
        assertThat(response.sourceVersion()).isEqualTo("sha-1");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.inputSnapshot().salesOrderLineIds()).containsExactly(lineId);
        // Stored at microsecond precision, so the response must already be: a later read must not differ.
        assertThat(response.completedAt().getNano() % 1_000).isZero();
    }

    @Test
    void get_commercialRunWhoseSourcesChanged_reportsStale_A26() {
        MrpRun run = MrpRun.builder().mrpRunId(UUID.randomUUID()).code("RUN-1").company(company).plant(plant)
                .horizonStartDate(LocalDate.now()).horizonEndDate(LocalDate.now())
                .status(MrpRunStatus.COMPLETED).runKind(MrpRunKind.COMMERCIAL).sourceVersion("v1").build();
        when(mrpRunRepository.findWithDetailsByMrpRunId(run.getMrpRunId())).thenReturn(Optional.of(run));
        when(sourceVersionCalculator.computeCurrent(run)).thenReturn("v2");

        assertThat(service.get(run.getMrpRunId()).currentSourceState()).isEqualTo("STALE");
    }

    @Test
    void get_standardRun_hasNoSourceStateAndNeverRecomputes() {
        MrpRun run = MrpRun.builder().mrpRunId(UUID.randomUUID()).code("RUN-1").company(company).plant(plant)
                .horizonStartDate(LocalDate.now()).horizonEndDate(LocalDate.now()).status(MrpRunStatus.COMPLETED).build();
        when(mrpRunRepository.findWithDetailsByMrpRunId(run.getMrpRunId())).thenReturn(Optional.of(run));

        assertThat(service.get(run.getMrpRunId()).currentSourceState()).isNull();
        verifyNoInteractions(sourceVersionCalculator);
    }

    /** Converted / remaining per line come from one proposal query and one ledger query (C14). */
    @Test
    void listRequirements_reportsConvertedAndRemainingFromTheLedgerInTwoBatchQueries() {
        MrpRun run = MrpRun.builder().mrpRunId(UUID.randomUUID()).company(company).plant(plant).build();
        MrpRequirementLine line = MrpRequirementLine.builder()
                .mrpRequirementLineId(UUID.randomUUID()).mrpRun(run).item(product).requirementLevel(0)
                .grossRequiredQuantity(new BigDecimal("70")).netRequiredQuantity(new BigDecimal("70"))
                .dueDate(LocalDate.now()).requirementStatus(MrpRequirementStatus.SHORTAGE).build();
        SupplySuggestion suggestion = SupplySuggestion.builder()
                .supplySuggestionId(UUID.randomUUID()).mrpRun(run).requirementLine(line).company(company).plant(plant)
                .item(product).suggestionType(SupplySuggestionType.WORK_ORDER).suggestedQuantity(new BigDecimal("70"))
                .neededByDate(LocalDate.now()).suggestedOrderDate(LocalDate.now())
                .status(SupplySuggestionStatus.CONVERTED).build();
        when(mrpRunRepository.existsById(run.getMrpRunId())).thenReturn(true);
        when(requirementLineRepository.findByMrpRunMrpRunId(eq(run.getMrpRunId()), any()))
                .thenReturn(new PageImpl<>(List.of(line), PageRequest.of(0, 50), 1));
        when(supplySuggestionRepository.findByRequirementLineIds(List.of(line.getMrpRequirementLineId())))
                .thenReturn(List.of(suggestion));
        when(conversionRepository.sumBySuggestionIds(List.of(suggestion.getSupplySuggestionId())))
                .thenReturn(List.of(new SupplySuggestionConversionRepository.SuggestionQuantityProjection() {
                    @Override public UUID getSupplySuggestionId() { return suggestion.getSupplySuggestionId(); }
                    @Override public BigDecimal getQuantity() { return new BigDecimal("40"); }
                    @Override public Long getRowCount() { return 1L; }
                }));

        MrpRequirementLineResponse response = service.listRequirements(run.getMrpRunId(), PageRequest.of(0, 50))
                .content().get(0);

        assertThat(response.supplyType()).isEqualTo("MAKE");
        assertThat(response.sourceKind()).isNull();
        assertThat(response.convertedQuantity()).isEqualByComparingTo("40");
        assertThat(response.remainingConvertibleQuantity()).isEqualByComparingTo("30");
        assertThat(response.calculationState()).isEqualTo("CALCULATED");
        verify(supplySuggestionRepository, times(1)).findByRequirementLineIds(any());
        verify(conversionRepository, times(1)).sumBySuggestionIds(any());
    }

    // ── fixtures ────────────────────────────────────────────────────────

    private CommercialPlanningRunRequest validRequest(List<ItemWarehouseMappingRequest> mappings) {
        return request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), mappings, false);
    }

    private CommercialPlanningRunRequest request(LocalDate start, LocalDate end,
                                                 List<ItemWarehouseMappingRequest> mappings, Boolean includeRop) {
        return new CommercialPlanningRunRequest(company.getCompanyId(), plant.getPlantId(), start, end,
                List.of(lineId), mappings, includeRop);
    }

    private void stubOrganization() {
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
    }

    private SalesOrderPlanningSource source(UUID plantId, boolean eligible, String uncovered) {
        return new SalesOrderPlanningSource(lineId, UUID.randomUUID(), "SO-000001", 1, company.getCompanyId(), plantId,
                "CONFIRMED", eligible, product.getItemId(), "EA", new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal("30"), BigDecimal.ZERO, new BigDecimal(uncovered), LocalDate.of(2026, 1, 20));
    }

    private void stubRunCreation() {
        Map<UUID, MrpRun> started = new HashMap<>();
        when(runStateRecorder.start(any(MrpRun.class))).thenAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            run.setMrpRunId(UUID.randomUUID());
            started.put(run.getMrpRunId(), run);
            return run.getMrpRunId();
        });
        when(mrpRunRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(started.get(invocation.getArgument(0))));
    }
}
