package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
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
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionConversionRepository;
import com.erp.manufacturing.module.planning.dto.PendingQcSupplyResponse;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSourceService;
import com.erp.manufacturing.common.response.PageResult;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import java.util.HashMap;
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
    @Mock MrpRunStateRecorder runStateRecorder;
    @Mock SalesOrderPlanningSourceService salesOrderPlanningSourceService;
    @Mock PlanningSourceVersionCalculator sourceVersionCalculator;
    @Mock SupplySuggestionConversionRepository conversionRepository;
    @Mock ItemLookupService itemLookupService;
    @Mock PurchaseQcLookupService purchaseQcLookupService;
    @Mock MrpPendingQcSourceRepository pendingQcSourceRepository;

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
                new IdempotencySupport(new ObjectMapper().findAndRegisterModules()),
                runStateRecorder,
                salesOrderPlanningSourceService,
                sourceVersionCalculator,
                conversionRepository,
                itemLookupService,
                purchaseQcLookupService,
                pendingQcSourceRepository);
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
        stubRunCreation();
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
        stubRunCreation();
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
        stubRunCreation();
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

    // ── CF-4: purchase stock awaiting QC is shown next to a requirement, never netted ─────────────

    /**
     * FE fixture: demand 40, open PO 15, available 0, 25 received but awaiting QC. The calculation (mocked, so
     * unchanged) says net 25 — and the persisted line still says 25: the pending 25 sits next to it, it does not cover
     * it. One source row carries every field of the receipt line it came from; the BUY proposal warns, READY ⇒ WARNING.
     */
    @Test
    void run_pendingQc_isShownNextToAnUnchangedNet_andTheBuyProposalWarns() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item item = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        MrpCalculationService.RequirementDraft draft = draft(item, warehouse, "40", "0", "15", "15", "25");
        PurchaseQcLookupService.PendingQcLine pending = pendingLine(item, warehouse.getWarehouseId(), "25");
        when(purchaseQcLookupService.pendingQualityLines(anyCollection(), anyCollection())).thenReturn(List.of(pending));

        UUID runId = runWith(company, plant, warehouse, null, new MrpCalculationService.MrpCalculationResult(
                List.of(draft), List.of(suggestion(draft, SupplySuggestionType.PURCHASE_REQUISITION,
                        SupplySuggestionExceptionState.READY))));

        MrpRequirementLine line = singleSavedLine();
        // Netting untouched: every quantity is the draft's own.
        assertThat(line.getGrossRequiredQuantity()).isEqualByComparingTo("40");
        assertThat(line.getAvailableQuantity()).isEqualByComparingTo("0");
        assertThat(line.getOpenSupplyQuantity()).isEqualByComparingTo("15");
        assertThat(line.getProjectedAvailableQuantity()).isEqualByComparingTo("15");
        assertThat(line.getNetRequiredQuantity()).isEqualByComparingTo("25");
        assertThat(line.getPendingQcQuantity()).isEqualByComparingTo("25");
        assertThat(line.getPendingQcSourceCount()).isEqualTo(1);

        List<MrpPendingQcSource> sources = savedSources();
        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.getMrpRunId()).isEqualTo(runId);
            assertThat(source.getRequirementLineId()).isEqualTo(line.getMrpRequirementLineId());
            assertThat(source.getGoodsReceiptId()).isEqualTo(pending.goodsReceiptId());
            assertThat(source.getGoodsReceiptLineId()).isEqualTo(pending.goodsReceiptLineId());
            assertThat(source.getReceiptCode()).isEqualTo("GR-000025");
            assertThat(source.getReceiptVersion()).isEqualTo(3L);
            assertThat(source.getItemId()).isEqualTo(item.getItemId());
            assertThat(source.getWarehouseId()).isEqualTo(warehouse.getWarehouseId());
            assertThat(source.getUomCode()).isEqualTo("KG");
            assertThat(source.getPendingQuantity()).isEqualByComparingTo("25");
        });

        SupplySuggestion proposal = singleSavedSuggestion();
        assertThat(proposal.getExceptionState()).isEqualTo(SupplySuggestionExceptionState.WARNING);
        assertThat(proposal.messages()).containsExactly("MATERIAL_SHORTAGE", "PENDING_QC_SUPPLY");
        assertThat(proposal.getSuggestedQuantity()).isEqualByComparingTo("25");

        // One lookup for the whole run (C14), for the run's items and warehouses.
        verify(purchaseQcLookupService, times(1)).pendingQualityLines(any(), any());
        verify(purchaseQcLookupService).pendingQualityLines(
                eq(java.util.Set.of(item.getItemId())), eq(java.util.Set.of(warehouse.getWarehouseId())));
    }

    /** The warning is for BUY only, and it never downgrades: BLOCKED stays BLOCKED, WARNING stays WARNING. */
    @Test
    void run_pendingQcWarning_isForBuyOnly_andNeverDowngradesBlockedOrWarning() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item make = item(UUID.randomUUID(), company, ItemType.FINISHED_GOOD);
        Item blockedBuy = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        Item warningBuy = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        MrpCalculationService.RequirementDraft makeDraft = draft(make, warehouse, "10", "0", "0", "0", "10");
        MrpCalculationService.RequirementDraft blockedDraft = draft(blockedBuy, warehouse, "10", "0", "0", "0", "10");
        MrpCalculationService.RequirementDraft warningDraft = draft(warningBuy, warehouse, "10", "0", "0", "0", "10");
        UUID wh = warehouse.getWarehouseId();
        when(purchaseQcLookupService.pendingQualityLines(anyCollection(), anyCollection())).thenReturn(List.of(
                pendingLine(make, wh, "4"), pendingLine(blockedBuy, wh, "5"), pendingLine(warningBuy, wh, "6")));

        runWith(company, plant, warehouse, null, new MrpCalculationService.MrpCalculationResult(
                List.of(makeDraft, blockedDraft, warningDraft),
                List.of(suggestion(makeDraft, SupplySuggestionType.WORK_ORDER, SupplySuggestionExceptionState.READY),
                        suggestion(blockedDraft, SupplySuggestionType.PURCHASE_REQUISITION,
                                SupplySuggestionExceptionState.BLOCKED),
                        suggestion(warningDraft, SupplySuggestionType.PURCHASE_REQUISITION,
                                SupplySuggestionExceptionState.WARNING))));

        Map<UUID, SupplySuggestion> byItem = new HashMap<>();
        savedSuggestions().forEach(s -> byItem.put(s.getItem().getItemId(), s));
        // MAKE: the pending stock is still captured on its line, but its proposal is untouched.
        assertThat(byItem.get(make.getItemId()).getExceptionState()).isEqualTo(SupplySuggestionExceptionState.READY);
        assertThat(byItem.get(make.getItemId()).messages()).containsExactly("MATERIAL_SHORTAGE");
        assertThat(byItem.get(blockedBuy.getItemId()).getExceptionState())
                .isEqualTo(SupplySuggestionExceptionState.BLOCKED);
        assertThat(byItem.get(blockedBuy.getItemId()).messages()).contains("PENDING_QC_SUPPLY");
        assertThat(byItem.get(warningBuy.getItemId()).getExceptionState())
                .isEqualTo(SupplySuggestionExceptionState.WARNING);
        assertThat(byItem.get(warningBuy.getItemId()).messages())
                .containsExactly("MATERIAL_SHORTAGE", "PENDING_QC_SUPPLY");
        assertThat(savedLines()).extracting(MrpRequirementLine::getPendingQcQuantity)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("4"), new BigDecimal("5"), new BigDecimal("6"));
    }

    /**
     * A line without a warehouse is matched against the run's scope warehouses (the set its netting used); a line with
     * a warehouse only against its own. Receipt lines outside both, or of another item, are left out.
     */
    @Test
    void run_lineWithoutWarehouseUsesTheScope_lineWithWarehouseOnlyItsOwn() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse w1 = warehouse(UUID.randomUUID(), plant);
        UUID w2 = UUID.randomUUID();
        UUID w3 = UUID.randomUUID();
        Item noWarehouseItem = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        Item warehouseItem = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        MrpCalculationService.RequirementDraft noWarehouse = draft(noWarehouseItem, null, "30", "0", "0", "0", "30");
        MrpCalculationService.RequirementDraft withWarehouse = draft(warehouseItem, w1, "20", "0", "0", "0", "20");
        when(purchaseQcLookupService.pendingQualityLines(anyCollection(), anyCollection())).thenReturn(List.of(
                pendingLine(noWarehouseItem, w1.getWarehouseId(), "10"),
                pendingLine(noWarehouseItem, w2, "5"),
                pendingLine(noWarehouseItem, w3, "7"),          // outside the scope
                pendingLine(warehouseItem, w1.getWarehouseId(), "4"),
                pendingLine(warehouseItem, w2, "6")));           // in scope, but not the line's warehouse

        runWith(company, plant, null, List.of(w1.getWarehouseId(), w2), new MrpCalculationService.MrpCalculationResult(
                List.of(noWarehouse, withWarehouse), List.of()));

        List<MrpRequirementLine> lines = savedLines();
        assertThat(lines.get(0).getPendingQcQuantity()).isEqualByComparingTo("15");
        assertThat(lines.get(0).getPendingQcSourceCount()).isEqualTo(2);
        assertThat(lines.get(1).getPendingQcQuantity()).isEqualByComparingTo("4");
        assertThat(lines.get(1).getPendingQcSourceCount()).isEqualTo(1);
        assertThat(savedSources()).extracting(MrpPendingQcSource::getWarehouseId)
                .containsExactly(w1.getWarehouseId(), w2, w1.getWarehouseId());
        verify(purchaseQcLookupService, times(1)).pendingQualityLines(any(), any());
        verify(purchaseQcLookupService).pendingQualityLines(
                eq(java.util.Set.of(noWarehouseItem.getItemId(), warehouseItem.getItemId())),
                eq(java.util.Set.of(w1.getWarehouseId(), w2)));
    }

    /** Captured and nothing pending is zero, not "not captured" (null is reserved for runs before V93). */
    @Test
    void run_withoutPendingQc_recordsZeroNotNull_andAddsNoWarning() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Item item = item(UUID.randomUUID(), company, ItemType.RAW_MATERIAL);
        MrpCalculationService.RequirementDraft draft = draft(item, warehouse, "40", "0", "15", "15", "25");
        when(purchaseQcLookupService.pendingQualityLines(anyCollection(), anyCollection())).thenReturn(List.of());

        runWith(company, plant, warehouse, null, new MrpCalculationService.MrpCalculationResult(
                List.of(draft), List.of(suggestion(draft, SupplySuggestionType.PURCHASE_REQUISITION,
                        SupplySuggestionExceptionState.READY))));

        MrpRequirementLine line = singleSavedLine();
        assertThat(line.getPendingQcQuantity()).isNotNull().isEqualByComparingTo("0");
        assertThat(line.getPendingQcSourceCount()).isZero();
        assertThat(savedSources()).isEmpty();
        SupplySuggestion proposal = singleSavedSuggestion();
        assertThat(proposal.getExceptionState()).isEqualTo(SupplySuggestionExceptionState.READY);
        assertThat(proposal.messages()).containsExactly("MATERIAL_SHORTAGE");
        verify(purchaseQcLookupService, times(1)).pendingQualityLines(any(), any());
    }

    /**
     * FE R05: what an old run showed is what it captured. Listing reads only the snapshot table — never the live QC
     * lookup, so a decision made after the run cannot change the answer.
     */
    @Test
    void listPendingQcSupplies_readsTheRunsSnapshotOnly_neverTheLiveQc() {
        UUID runId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        MrpPendingQcSource source = MrpPendingQcSource.builder()
                .sourceId(UUID.randomUUID()).mrpRunId(runId).requirementLineId(requirementId)
                .goodsReceiptId(UUID.randomUUID()).goodsReceiptLineId(UUID.randomUUID())
                .receiptCode("GR-000025").receiptVersion(3L).itemId(UUID.randomUUID()).warehouseId(warehouseId)
                .uomCode("KG").pendingQuantity(new BigDecimal("25")).build();
        PageRequest pageable = PageRequest.of(0, 20);
        when(mrpRunRepository.existsById(runId)).thenReturn(true);
        when(pendingQcSourceRepository.search(runId, requirementId, warehouseId, pageable))
                .thenReturn(new PageImpl<>(List.of(source), pageable, 1));

        PageResult<PendingQcSupplyResponse> page =
                service.listPendingQcSupplies(runId, requirementId, warehouseId, pageable);

        assertThat(page.totalElements()).isEqualTo(1);
        PendingQcSupplyResponse row = page.content().get(0);
        assertThat(row.sourceId()).isEqualTo(source.getSourceId());
        assertThat(row.requirementLineId()).isEqualTo(requirementId);
        assertThat(row.goodsReceiptId()).isEqualTo(source.getGoodsReceiptId());
        assertThat(row.goodsReceiptLineId()).isEqualTo(source.getGoodsReceiptLineId());
        assertThat(row.receiptCode()).isEqualTo("GR-000025");
        assertThat(row.receiptVersion()).isEqualTo(3L);
        assertThat(row.itemId()).isEqualTo(source.getItemId());
        assertThat(row.warehouseId()).isEqualTo(warehouseId);
        assertThat(row.uomCode()).isEqualTo("KG");
        assertThat(row.pendingQuantity()).isEqualByComparingTo("25");
        verifyNoInteractions(purchaseQcLookupService);
    }

    @Test
    void listPendingQcSupplies_unknownRun_isNotFoundBeforeTheSnapshotIsRead() {
        UUID runId = UUID.randomUUID();
        when(mrpRunRepository.existsById(runId)).thenReturn(false);

        assertThatThrownBy(() -> service.listPendingQcSupplies(runId, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
        verifyNoInteractions(pendingQcSourceRepository, purchaseQcLookupService);
    }

    /** Runs a standard MRP run whose calculation returns {@code result}; returns the run id. */
    private UUID runWith(Company company, Plant plant, Warehouse runWarehouse, List<UUID> plantScope,
                         MrpCalculationService.MrpCalculationResult result) {
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        if (runWarehouse != null) {
            when(organizationLookupService.getActiveWarehouse(runWarehouse.getWarehouseId())).thenReturn(runWarehouse);
        } else {
            when(organizationLookupService.resolveScope(ScopeResourceType.PLANT, plant.getPlantId()))
                    .thenReturn(new OrganizationScopeResolution(
                            ScopeResourceType.PLANT, plant.getPlantId(), company.getCompanyId(), plantScope));
        }
        stubRunCreation();
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planningDemandRepository.findOpenDemandsForRun(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(calculationService.calculate(any(MrpRun.class), anyList(), anyCollection())).thenReturn(result);
        when(requirementLineRepository.save(any(MrpRequirementLine.class))).thenAnswer(invocation -> {
            MrpRequirementLine line = invocation.getArgument(0);
            line.setMrpRequirementLineId(UUID.randomUUID());
            return line;
        });

        MrpRunResponse response = service.run(new MrpRunCreateRequest(company.getCompanyId(), plant.getPlantId(),
                runWarehouse == null ? null : runWarehouse.getWarehouseId(),
                LocalDate.now(), LocalDate.now().plusDays(30), null), null);
        assertThat(response.status()).isEqualTo(MrpRunStatus.COMPLETED.name());
        return response.mrpRunId();
    }

    private MrpCalculationService.RequirementDraft draft(Item item, Warehouse warehouse, String gross, String available,
                                                         String openSupply, String projected, String net) {
        return new MrpCalculationService.RequirementDraft(null, null, item, warehouse, 0,
                new BigDecimal(gross), new BigDecimal(available), BigDecimal.ZERO, new BigDecimal(openSupply),
                BigDecimal.ZERO, new BigDecimal(projected), new BigDecimal(net), LocalDate.now().plusDays(10),
                MrpRequirementStatus.SHORTAGE, null, LocalDate.now().plusDays(5), PlanningSettingSource.ITEM_WAREHOUSE, 0);
    }

    private static MrpCalculationService.SuggestionDraft suggestion(MrpCalculationService.RequirementDraft requirement,
                                                                   SupplySuggestionType type,
                                                                   SupplySuggestionExceptionState state) {
        return new MrpCalculationService.SuggestionDraft(requirement, type, state,
                List.of(PlanningMessageCode.MATERIAL_SHORTAGE), null, null);
    }

    private static PurchaseQcLookupService.PendingQcLine pendingLine(Item item, UUID warehouseId, String quantity) {
        return new PurchaseQcLookupService.PendingQcLine(UUID.randomUUID(), UUID.randomUUID(), "GR-000025", 3L,
                item.getItemId(), warehouseId, "KG", new BigDecimal(quantity));
    }

    private List<MrpRequirementLine> savedLines() {
        ArgumentCaptor<MrpRequirementLine> captor = ArgumentCaptor.forClass(MrpRequirementLine.class);
        verify(requirementLineRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private MrpRequirementLine singleSavedLine() {
        List<MrpRequirementLine> lines = savedLines();
        assertThat(lines).hasSize(1);
        return lines.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<MrpPendingQcSource> savedSources() {
        ArgumentCaptor<Iterable<MrpPendingQcSource>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(pendingQcSourceRepository, atLeast(0)).saveAll(captor.capture());
        List<MrpPendingQcSource> all = new java.util.ArrayList<>();
        captor.getAllValues().forEach(batch -> batch.forEach(all::add));
        return all;
    }

    @SuppressWarnings("unchecked")
    private List<SupplySuggestion> savedSuggestions() {
        ArgumentCaptor<Iterable<SupplySuggestion>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(supplySuggestionRepository).saveAll(captor.capture());
        List<SupplySuggestion> all = new java.util.ArrayList<>();
        captor.getValue().forEach(all::add);
        return all;
    }

    private SupplySuggestion singleSavedSuggestion() {
        List<SupplySuggestion> suggestions = savedSuggestions();
        assertThat(suggestions).hasSize(1);
        return suggestions.get(0);
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
        verify(runStateRecorder, never()).start(any(MrpRun.class));
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

        verify(runStateRecorder, never()).start(any(MrpRun.class));
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
        stubRunCreation();
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.run(new MrpRunCreateRequest(
                company.getCompanyId(), plant.getPlantId(), null,
                LocalDate.now(), LocalDate.now().plusDays(30), null), "  run-key-2  ");

        ArgumentCaptor<MrpRun> saved = ArgumentCaptor.forClass(MrpRun.class);
        verify(runStateRecorder).start(saved.capture());
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
        stubRunCreation();
        when(mrpRunRepository.save(any(MrpRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.run(new MrpRunCreateRequest(
                company.getCompanyId(), plant.getPlantId(), null,
                LocalDate.now(), LocalDate.now().plusDays(30), null), null);

        verify(mrpRunRepository, never()).findByIdempotencyKey(any());
        ArgumentCaptor<MrpRun> saved = ArgumentCaptor.forClass(MrpRun.class);
        verify(runStateRecorder).start(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isNull();
        assertThat(saved.getValue().getPayloadHash()).isNull();
    }

    /**
     * EH-4. {@code MrpRunResponse.errorMessage} goes straight to the client, and before this it
     * carried {@code e.getMessage()} of whatever blew up — a Hibernate or driver string naming
     * tables, columns and constraints. That was the one place in this codebase where an internal
     * failure message reached a caller verbatim.
     */
    @Test
    @DisplayName("run: an unexpected failure is recorded with a fixed message, never the raw cause")
    void run_unexpectedFailure_recordsAFixedMessageInsteadOfTheRawCause() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(planningDemandRepository.findOpenDemandsForRun(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(organizationLookupService.resolveScope(eq(ScopeResourceType.PLANT), eq(plant.getPlantId())))
                .thenReturn(new OrganizationScopeResolution(
                        ScopeResourceType.PLANT, plant.getPlantId(), company.getCompanyId(), List.of()));
        stubRunCreation();
        when(calculationService.calculate(any(MrpRun.class), anyList(), anyCollection()))
                .thenThrow(new IllegalStateException(
                        "could not execute statement [ERROR: relation \"mrp_requirement_lines\" ...]"));

        service.run(new MrpRunCreateRequest(
                company.getCompanyId(), plant.getPlantId(), null,
                LocalDate.now(), LocalDate.now().plusDays(30), null), null);

        ArgumentCaptor<String> recorded = ArgumentCaptor.forClass(String.class);
        verify(runStateRecorder).recordFailure(any(UUID.class), recorded.capture());
        assertThat(recorded.getValue())
                .isEqualTo(MrpRunService.GENERIC_FAILURE_MESSAGE)
                .doesNotContain("mrp_requirement_lines");
        // The doomed transaction is not asked to write the FAILED row itself — that is the recorder's
        // job precisely because this one may already be unusable.
        verify(mrpRunRepository, never()).save(any(MrpRun.class));
    }

    /**
     * The other half of the same rule: an {@link AppException} was raised deliberately and its message
     * was written for the planner who asked for the run. Blanking that one out would turn a useful
     * "no warehouse policy resolves this requirement" into "see server logs".
     */
    @Test
    @DisplayName("run: a deliberate business failure keeps its own message")
    void run_businessFailure_keepsItsOwnMessage() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(planningDemandRepository.findOpenDemandsForRun(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(organizationLookupService.resolveScope(eq(ScopeResourceType.PLANT), eq(plant.getPlantId())))
                .thenReturn(new OrganizationScopeResolution(
                        ScopeResourceType.PLANT, plant.getPlantId(), company.getCompanyId(), List.of()));
        stubRunCreation();
        when(calculationService.calculate(any(MrpRun.class), anyList(), anyCollection()))
                .thenThrow(ExceptionFactory.custom(BusinessErrorCode.AMBIGUOUS_WAREHOUSE_POLICY,
                        "Multiple warehouses match item RM-01 without a default"));

        service.run(new MrpRunCreateRequest(
                company.getCompanyId(), plant.getPlantId(), null,
                LocalDate.now(), LocalDate.now().plusDays(30), null), null);

        ArgumentCaptor<String> recorded = ArgumentCaptor.forClass(String.class);
        verify(runStateRecorder).recordFailure(any(UUID.class), recorded.capture());
        assertThat(recorded.getValue()).isEqualTo("Multiple warehouses match item RM-01 without a default");
    }

    /**
     * EH-4: the run row is now committed by {@link MrpRunStateRecorder#start} in a transaction of its
     * own, and the service re-loads it. The recorder is mocked, so the test has to play both halves —
     * hand back an id and make {@code findById} answer with the very entity the service built, which
     * is what a real re-load would return.
     */
    private void stubRunCreation() {
        Map<UUID, MrpRun> started = new HashMap<>();
        when(runStateRecorder.start(any(MrpRun.class))).thenAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            if (run.getMrpRunId() == null) {
                run.setMrpRunId(UUID.randomUUID());
            }
            started.put(run.getMrpRunId(), run);
            return run.getMrpRunId();
        });
        when(mrpRunRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(started.get(invocation.getArgument(0))));
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
