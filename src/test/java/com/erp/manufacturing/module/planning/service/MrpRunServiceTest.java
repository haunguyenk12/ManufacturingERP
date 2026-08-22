package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.dto.MrpRunCreateRequest;
import com.erp.manufacturing.module.planning.dto.MrpRunResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                auditLogService,
                new IdempotencySupport(new ObjectMapper().findAndRegisterModules()));
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
                new BigDecimal("4"),
                new BigDecimal("10"),
                demand.getDueDate(),
                MrpRequirementStatus.SHORTAGE,
                null,
                demand.getDueDate(),
                PlanningSettingSource.ITEM_WAREHOUSE,
                2);

        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(organizationLookupService.getActiveWarehouse(warehouse.getWarehouseId())).thenReturn(warehouse);
        when(mrpRunRepository.saveAndFlush(any(MrpRun.class))).thenAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            if (run.getMrpRunId() == null) {
                run.setMrpRunId(UUID.randomUUID());
            }
            return run;
        });
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
                                requirement,
                                SupplySuggestionType.PURCHASE_REQUISITION,
                                SupplySuggestionExceptionState.WARNING,
                                List.of(PlanningMessageCode.MATERIAL_SHORTAGE,
                                        PlanningMessageCode.SYSTEM_FALLBACK_USED),
                                "RT-9",
                                "3"))));
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
                LocalDate.now().plusDays(30),
                null), null);

        assertThat(response.status()).isEqualTo(MrpRunStatus.COMPLETED.name());
        assertThat(response.totalDemandLines()).isEqualTo(1);
        assertThat(response.totalRequirementLines()).isEqualTo(1);
        assertThat(response.totalSuggestionLines()).isEqualTo(1);
        verify(mrpRunDemandRepository).saveAll(argThat(snapshots -> snapshots.iterator().hasNext()));
        verify(requirementLineRepository).save(argThat(line ->
                line.getSettingSource() == PlanningSettingSource.ITEM_WAREHOUSE
                        && line.getExcludedLotCount() == 2
                        // F10 / debt G: the coverage the netting actually used reaches the row.
                        && line.getProjectedAvailableQuantity().compareTo(new BigDecimal("4")) == 0));
        verify(supplySuggestionRepository).saveAll(argThat(suggestions -> {
            SupplySuggestion saved = suggestions.iterator().next();
            return saved.getExceptionState() == SupplySuggestionExceptionState.WARNING
                    && saved.messages().equals(List.of("MATERIAL_SHORTAGE", "SYSTEM_FALLBACK_USED"))
                    // F10 / debt F: the frozen routing snapshot reaches the row too.
                    && "RT-9".equals(saved.getSourceRoutingCode())
                    && "3".equals(saved.getSourceRoutingVersion());
        }));
    }

    /**
     * D4.3 / debt #15: the four Run-header cells of spec §2.4. The counts are read off the calculation
     * result, so a run holding a covered line, a blocked MAKE proposal and a BUY proposal at once is
     * what tells the four counters apart.
     *
     * <p>The run {@code code} is not asserted here: it is derived in {@code @PrePersist}, which a mocked
     * repository never fires. It is covered against a real insert in {@code ProductionFlowE2EIT}.
     */
    @Test
    void run_countsTheFourSummaryCellsOfTheRunHeader() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item makeItem = item(UUID.randomUUID(), company, ItemType.FINISHED_GOOD);
        Item buyItem = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        PlanningDemand demand = demand(company, plant, warehouse, makeItem);

        MrpCalculationService.RequirementDraft shortMake =
                requirement(demand, makeItem, warehouse, "10", MrpRequirementStatus.BOM_MISSING);
        MrpCalculationService.RequirementDraft shortBuy =
                requirement(demand, buyItem, warehouse, "4", MrpRequirementStatus.SHORTAGE);
        MrpCalculationService.RequirementDraft covered =
                requirement(demand, buyItem, warehouse, "0", MrpRequirementStatus.COVERED);

        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(organizationLookupService.getActiveWarehouse(warehouse.getWarehouseId())).thenReturn(warehouse);
        when(mrpRunRepository.saveAndFlush(any(MrpRun.class))).thenAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            if (run.getMrpRunId() == null) {
                run.setMrpRunId(UUID.randomUUID());
            }
            return run;
        });
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planningDemandRepository.findOpenDemandsForRun(
                company.getCompanyId(),
                plant.getPlantId(),
                warehouse.getWarehouseId(),
                LocalDate.now(),
                LocalDate.now().plusDays(30))).thenReturn(List.of(demand));
        when(calculationService.calculate(any(MrpRun.class), eq(List.of(demand)), anyCollection()))
                .thenReturn(new MrpCalculationService.MrpCalculationResult(
                        List.of(shortMake, shortBuy, covered),
                        List.of(
                                new MrpCalculationService.SuggestionDraft(
                                        shortMake,
                                        SupplySuggestionType.WORK_ORDER,
                                        SupplySuggestionExceptionState.BLOCKED,
                                        List.of(PlanningMessageCode.MATERIAL_SHORTAGE,
                                                PlanningMessageCode.MISSING_BOM),
                                        null,
                                        null),
                                new MrpCalculationService.SuggestionDraft(
                                        shortBuy,
                                        SupplySuggestionType.PURCHASE_REQUISITION,
                                        SupplySuggestionExceptionState.READY,
                                        List.of(PlanningMessageCode.MATERIAL_SHORTAGE),
                                        null,
                                        null))));
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
                LocalDate.now().plusDays(30),
                null), null);

        assertThat(response.shortageLines()).isEqualTo(2);            // the covered line is not a shortage
        assertThat(response.plannedWorkOrders()).isEqualTo(1);
        assertThat(response.plannedPurchaseRecommendations()).isEqualTo(1);
        assertThat(response.blockedProposals()).isEqualTo(1);
        assertThat(response.totalRequirementLines()).isEqualTo(3);
        assertThat(response.totalSuggestionLines()).isEqualTo(2);
    }

    @Test
    void run_withDemandLineIds_plansOnlyTheSelectedDemandsAndSkipsTheHorizonSweep() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item item = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        PlanningDemand selected = demand(company, plant, warehouse, item);

        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(organizationLookupService.getActiveWarehouse(warehouse.getWarehouseId())).thenReturn(warehouse);
        when(planningDemandRepository.findSelectedDemandsForRun(List.of(selected.getPlanningDemandId())))
                .thenReturn(List.of(selected));
        when(mrpRunRepository.saveAndFlush(any(MrpRun.class))).thenAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            if (run.getMrpRunId() == null) {
                run.setMrpRunId(UUID.randomUUID());
            }
            return run;
        });
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(calculationService.calculate(any(MrpRun.class), eq(List.of(selected)), anyCollection()))
                .thenReturn(new MrpCalculationService.MrpCalculationResult(List.of(), List.of()));

        MrpRunResponse response = service.run(new MrpRunCreateRequest(
                company.getCompanyId(),
                plant.getPlantId(),
                warehouse.getWarehouseId(),
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                List.of(selected.getPlanningDemandId())), null);

        assertThat(response.totalDemandLines()).isEqualTo(1);
        verify(planningDemandRepository).findSelectedDemandsForRun(List.of(selected.getPlanningDemandId()));
        verify(planningDemandRepository, never()).findOpenDemandsForRun(any(), any(), any(), any(), any());
        verify(calculationService).calculate(any(MrpRun.class), eq(List.of(selected)), anyCollection());
    }

    @Test
    void run_demandLineIdOfAnotherPlant_isRejectedAsNotFoundBeforeTheRunIsCreated() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Plant otherPlant = plant(UUID.randomUUID(), company);
        Item item = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        PlanningDemand foreignDemand = demand(company, otherPlant, null, item);

        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(planningDemandRepository.findSelectedDemandsForRun(List.of(foreignDemand.getPlanningDemandId())))
                .thenReturn(List.of(foreignDemand));

        assertThatThrownBy(() -> service.run(new MrpRunCreateRequest(
                company.getCompanyId(),
                plant.getPlantId(),
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                List.of(foreignDemand.getPlanningDemandId())), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));

        verifyNoInteractions(mrpRunRepository, mrpRunDemandRepository, calculationService);
    }

    @Test
    void run_cancelledDemandLineId_isRejectedWithStateConflict() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Item item = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        PlanningDemand cancelled = demand(company, plant, null, item);
        cancelled.setStatus(PlanningDemandStatus.CANCELLED);

        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(planningDemandRepository.findSelectedDemandsForRun(List.of(cancelled.getPlanningDemandId())))
                .thenReturn(List.of(cancelled));

        assertThatThrownBy(() -> service.run(new MrpRunCreateRequest(
                company.getCompanyId(),
                plant.getPlantId(),
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                List.of(cancelled.getPlanningDemandId())), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(mrpRunRepository, mrpRunDemandRepository, calculationService);
    }

    private MrpCalculationService.RequirementDraft requirement(PlanningDemand demand,
                                                              Item item,
                                                              Warehouse warehouse,
                                                              String netRequiredQuantity,
                                                              MrpRequirementStatus status) {
        return new MrpCalculationService.RequirementDraft(
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
                BigDecimal.ZERO,
                new BigDecimal(netRequiredQuantity),
                demand.getDueDate(),
                status,
                null,
                demand.getDueDate(),
                PlanningSettingSource.ITEM_WAREHOUSE,
                0);
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

    /**
     * The frontend sent one Idempotency-Key three times with an identical body and got three runs
     * (RUN-63033D7C / RUN-EA182AFB / RUN-4C77B225) — the header was ignored. Each duplicate run
     * produces its own parallel set of supply suggestions for the same demand, which is how a
     * conversion later "went missing": it had been done on a different run's proposal.
     */
    @Test
    @DisplayName("run: replaying a key with the same payload returns the first run without "
            + "recalculating anything")
    void run_replayedKeyWithSamePayload_returnsTheExistingRunAndDoesNotRecalculate() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        MrpRunCreateRequest request = new MrpRunCreateRequest(
                company.getCompanyId(), plant.getPlantId(), null,
                LocalDate.now(), LocalDate.now().plusDays(30), null);
        MrpRun existing = existingRun(company, plant, request);
        when(mrpRunRepository.findByIdempotencyKey("run-key-1")).thenReturn(Optional.of(existing));

        MrpRunResponse response = service.run(request, "run-key-1");

        assertThat(response.mrpRunId()).isEqualTo(existing.getMrpRunId());
        assertThat(response.code()).isEqualTo(existing.getCode());
        // The whole point: no second calculation, no second run row, no demand snapshot.
        verifyNoInteractions(calculationService, mrpRunDemandRepository, planningDemandRepository);
        verify(mrpRunRepository, never()).saveAndFlush(any(MrpRun.class));
        verify(mrpRunRepository, never()).save(any(MrpRun.class));
    }

    @Test
    @DisplayName("run: replaying a key with a different payload is 409 IDEMPOTENCY_CONFLICT, not a "
            + "silent replay of the first run")
    void run_replayedKeyWithDifferentPayload_throwsIdempotencyConflictBeforeAnyWrite() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        MrpRunCreateRequest first = new MrpRunCreateRequest(
                company.getCompanyId(), plant.getPlantId(), null,
                LocalDate.now(), LocalDate.now().plusDays(30), null);
        MrpRun existing = existingRun(company, plant, first);
        when(mrpRunRepository.findByIdempotencyKey("run-key-1")).thenReturn(Optional.of(existing));

        MrpRunCreateRequest changedHorizon = new MrpRunCreateRequest(
                company.getCompanyId(), plant.getPlantId(), null,
                LocalDate.now(), LocalDate.now().plusDays(60), null);

        assertThatThrownBy(() -> service.run(changedHorizon, "run-key-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.IDEMPOTENCY_CONFLICT));

        verify(mrpRunRepository, never()).saveAndFlush(any(MrpRun.class));
        verifyNoInteractions(calculationService, mrpRunDemandRepository);
    }

    @Test
    @DisplayName("run: a fresh key is stored on the run row together with the payload fingerprint")
    void run_freshKey_isPersistedOnTheRunWithItsPayloadHash() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        when(mrpRunRepository.findByIdempotencyKey("run-key-2")).thenReturn(Optional.empty());
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(planningDemandRepository.findOpenDemandsForRun(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(organizationLookupService.resolveScope(eq(ScopeResourceType.PLANT), eq(plant.getPlantId())))
                .thenReturn(new OrganizationScopeResolution(
                        ScopeResourceType.PLANT, plant.getPlantId(), company.getCompanyId(), List.of()));
        when(calculationService.calculate(any(MrpRun.class), anyList(), anyCollection()))
                .thenReturn(new MrpCalculationService.MrpCalculationResult(List.of(), List.of()));
        when(mrpRunRepository.saveAndFlush(any(MrpRun.class))).thenAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            run.setMrpRunId(UUID.randomUUID());
            return run;
        });
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.run(new MrpRunCreateRequest(
                company.getCompanyId(), plant.getPlantId(), null,
                LocalDate.now(), LocalDate.now().plusDays(30), null), "  run-key-2  ");

        ArgumentCaptor<MrpRun> saved = ArgumentCaptor.forClass(MrpRun.class);
        verify(mrpRunRepository).saveAndFlush(saved.capture());
        // Trimmed by normalizeKey — the stored key must be what a replay will look up.
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("run-key-2");
        assertThat(saved.getValue().getPayloadHash()).isNotBlank();
    }

    @Test
    @DisplayName("run: without the header nothing changes — no replay lookup, a new run every time")
    void run_withoutAnIdempotencyKey_neverConsultsTheReplayLookup() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(planningDemandRepository.findOpenDemandsForRun(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(organizationLookupService.resolveScope(eq(ScopeResourceType.PLANT), eq(plant.getPlantId())))
                .thenReturn(new OrganizationScopeResolution(
                        ScopeResourceType.PLANT, plant.getPlantId(), company.getCompanyId(), List.of()));
        when(calculationService.calculate(any(MrpRun.class), anyList(), anyCollection()))
                .thenReturn(new MrpCalculationService.MrpCalculationResult(List.of(), List.of()));
        when(mrpRunRepository.saveAndFlush(any(MrpRun.class))).thenAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            run.setMrpRunId(UUID.randomUUID());
            return run;
        });
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.run(new MrpRunCreateRequest(
                company.getCompanyId(), plant.getPlantId(), null,
                LocalDate.now(), LocalDate.now().plusDays(30), null), null);

        verify(mrpRunRepository, never()).findByIdempotencyKey(any());
        ArgumentCaptor<MrpRun> saved = ArgumentCaptor.forClass(MrpRun.class);
        verify(mrpRunRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isNull();
        assertThat(saved.getValue().getPayloadHash()).isNull();
    }

    private MrpRun existingRun(Company company, Plant plant, MrpRunCreateRequest request) {
        MrpRun run = MrpRun.builder()
                .mrpRunId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .horizonStartDate(request.horizonStartDate())
                .horizonEndDate(request.horizonEndDate())
                .idempotencyKey("run-key-1")
                .payloadHash(new IdempotencySupport(new ObjectMapper().findAndRegisterModules()).payloadHash(request))
                .build();
        run.assignCode();
        return run;
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
