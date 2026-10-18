package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.dto.PlanningConversionEntryRequest;
import com.erp.manufacturing.module.planning.dto.PlanningConversionRequest;
import com.erp.manufacturing.module.planning.dto.PlanningConversionResult;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.MrpRequirementLineRepository;
import com.erp.manufacturing.module.planning.repository.MrpRunRepository;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionConversionRepository;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionRepository;
import com.erp.manufacturing.module.purchasing.dto.PlanningPurchaseLine;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderLineResponse;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderResponse;
import com.erp.manufacturing.module.purchasing.service.PurchaseOrderService;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSource;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSourceService;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderCreateRequest;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderResponse;
import com.erp.manufacturing.module.workorder.service.WorkOrderService;
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
@DisplayName("PlanningConversionService tests (G6b)")
class PlanningConversionServiceTest {

    @Mock MrpRunRepository mrpRunRepository;
    @Mock MrpRequirementLineRepository requirementLineRepository;
    @Mock SupplySuggestionRepository supplySuggestionRepository;
    @Mock SupplySuggestionConversionRepository conversionRepository;
    @Mock PlanningSourceVersionCalculator sourceVersionCalculator;
    @Mock SalesOrderPlanningSourceService salesOrderPlanningSourceService;
    @Mock WorkOrderService workOrderService;
    @Mock PurchaseOrderService purchaseOrderService;

    PlanningConversionService service;
    final IdempotencySupport idempotency = new IdempotencySupport(new ObjectMapper().findAndRegisterModules());

    Company company;
    Plant plant;
    Warehouse warehouse;
    MrpRun run;
    UUID salesOrderLineId;

    @BeforeEach
    void setUp() {
        service = new PlanningConversionService(
                mrpRunRepository, requirementLineRepository, supplySuggestionRepository, conversionRepository,
                sourceVersionCalculator, salesOrderPlanningSourceService, workOrderService, purchaseOrderService,
                new MrpPlanningMapper(), idempotency);
        company = Company.builder().companyId(UUID.randomUUID()).code("C").name("C").status(OrganizationStatus.ACTIVE).build();
        plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").name("P")
                .timezone("Asia/Ho_Chi_Minh").status(OrganizationStatus.ACTIVE).build();
        warehouse = Warehouse.builder().warehouseId(UUID.randomUUID()).plant(plant).code("W").name("W")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build();
        run = MrpRun.builder()
                .mrpRunId(UUID.randomUUID())
                .code("RUN-TEST0001")
                .company(company)
                .plant(plant)
                .horizonStartDate(LocalDate.now())
                .horizonEndDate(LocalDate.now().plusDays(30))
                .status(MrpRunStatus.COMPLETED)
                .runKind(MrpRunKind.COMMERCIAL)
                .sourceVersion("v1")
                .conversionVersion(0)
                .build();
        salesOrderLineId = UUID.randomUUID();
    }

    @Test
    void convert_firstPartialMake_createsADraftWorkOrderCappedByTheUncoveredQuantity_A27() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.WORK_ORDER, 0, "70");
        stubOpenRun();
        stubSuggestions(suggestion);
        when(salesOrderPlanningSourceService.findSources(argThat(ids -> ids.contains(salesOrderLineId))))
                .thenReturn(Map.of(salesOrderLineId, source("70")));
        UUID workOrderId = UUID.randomUUID();
        when(workOrderService.createFromMrp(eq(plant.getPlantId()), any(), eq(salesOrderLineId), any(), any()))
                .thenReturn(workOrderResponse(workOrderId, "MRP-AAA"));

        PlanningConversionResult result = service.convert(run.getMrpRunId(),
                request(0, "v1", null, entry(suggestion, "40")), null);

        ArgumentCaptor<WorkOrderCreateRequest> created = ArgumentCaptor.forClass(WorkOrderCreateRequest.class);
        ArgumentCaptor<WorkOrderService.PlanningLineage> lineage = ArgumentCaptor.forClass(WorkOrderService.PlanningLineage.class);
        verify(workOrderService).createFromMrp(eq(plant.getPlantId()), created.capture(), eq(salesOrderLineId),
                lineage.capture(), eq(new BigDecimal("70")));
        assertThat(created.getValue().plannedQuantity()).isEqualByComparingTo("40");
        assertThat(created.getValue().outputWarehouseId()).isEqualTo(warehouse.getWarehouseId());
        assertThat(lineage.getValue().proposalId()).isEqualTo(suggestion.getSupplySuggestionId());
        assertThat(result.conversionVersion()).isEqualTo(1);
        assertThat(result.documents()).singleElement().satisfies(document -> {
            assertThat(document.type()).isEqualTo("WORK_ORDER");
            assertThat(document.documentId()).isEqualTo(workOrderId);
            assertThat(document.quantity()).isEqualByComparingTo("40");
        });
        assertThat(result.conversions()).singleElement().satisfies(row -> {
            assertThat(row.quantity()).isEqualByComparingTo("40");
            assertThat(row.targetId()).isEqualTo(workOrderId);
            assertThat(row.conversionVersion()).isEqualTo(1);
        });
        assertThat(run.getConversionVersion()).isEqualTo(1);
        assertThat(suggestion.getStatus()).isEqualTo(SupplySuggestionStatus.CONVERTED);
        verify(mrpRunRepository).saveAndFlush(run);
    }

    @Test
    void convert_moreThanTheRemainingQuantity_isRefusedPerEntryBeforeAnyDocument_A27() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.WORK_ORDER, 0, "70");
        suggestion.markConverted("WORK_ORDER", UUID.randomUUID());
        stubOpenRun();
        stubSuggestions(suggestion);
        stubLedger(suggestion, "40");

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "v1", null, entry(suggestion, "31")), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("entries[0].quantity");
                });
        verifyNoInteractions(workOrderService, purchaseOrderService);
        verify(conversionRepository, never()).saveAll(any());
    }

    @Test
    void convert_legacyConvertedProposal_hasNothingLeftToConvert() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.WORK_ORDER, 0, "70");
        suggestion.markConverted("WORK_ORDER", UUID.randomUUID());
        stubOpenRun();
        stubSuggestions(suggestion);
        when(conversionRepository.sumBySuggestionIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "v1", null, entry(suggestion, "1")), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED));
        verifyNoInteractions(workOrderService);
    }

    @Test
    void convert_staleConversionVersion_isConcurrentModificationBeforeAnyRead_A29() {
        when(mrpRunRepository.lockByMrpRunId(run.getMrpRunId())).thenReturn(Optional.of(run));
        run.setConversionVersion(1);

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "v1", null, new PlanningConversionEntryRequest(UUID.randomUUID(), BigDecimal.ONE)), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        verifyNoInteractions(sourceVersionCalculator, supplySuggestionRepository, workOrderService);
    }

    @Test
    void convert_tokenThatIsNotTheRunsOwn_isPlanningSourceStale() {
        when(mrpRunRepository.lockByMrpRunId(run.getMrpRunId())).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "other", null, new PlanningConversionEntryRequest(UUID.randomUUID(), BigDecimal.ONE)), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.PLANNING_SOURCE_STALE);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("expectedSourceVersion");
                });
        verifyNoInteractions(sourceVersionCalculator, workOrderService);
    }

    @Test
    void convert_sourcesChangedSinceTheRun_isPlanningSourceStaleInsideTheTransaction_A26() {
        when(mrpRunRepository.lockByMrpRunId(run.getMrpRunId())).thenReturn(Optional.of(run));
        when(sourceVersionCalculator.computeCurrent(run)).thenReturn("v2");

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "v1", null, new PlanningConversionEntryRequest(UUID.randomUUID(), BigDecimal.ONE)), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.PLANNING_SOURCE_STALE));
        verifyNoInteractions(supplySuggestionRepository, workOrderService, purchaseOrderService);
    }

    @Test
    void convert_standardRun_isStateConflict() {
        run.setRunKind(MrpRunKind.STANDARD);
        when(mrpRunRepository.lockByMrpRunId(run.getMrpRunId())).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "v1", null, new PlanningConversionEntryRequest(UUID.randomUUID(), BigDecimal.ONE)), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    /** A28: a component's work order makes a part, not what the customer ordered — no allocation. */
    @Test
    void convert_componentProposal_isNotAllocatedToTheSalesOrder_A28() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.WORK_ORDER, 1, "20");
        stubOpenRun();
        stubSuggestions(suggestion);
        when(salesOrderPlanningSourceService.findSources(any())).thenReturn(Map.of());
        when(workOrderService.createFromMrp(any(), any(), isNull(), any(), isNull()))
                .thenReturn(workOrderResponse(UUID.randomUUID(), "MRP-BBB"));

        service.convert(run.getMrpRunId(), request(0, "v1", null, entry(suggestion, "20")), null);

        verify(workOrderService).createFromMrp(eq(plant.getPlantId()), any(), isNull(), any(), isNull());
    }

    @Test
    void convert_blockedProposal_failsWithTheCodeThatBlockedIt() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.WORK_ORDER, 0, "70");
        suggestion.setExceptionState(SupplySuggestionExceptionState.BLOCKED);
        suggestion.setMessageCodes("MATERIAL_SHORTAGE,MISSING_ROUTING");
        stubOpenRun();
        stubSuggestions(suggestion);

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "v1", null, entry(suggestion, "1")), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.MISSING_ROUTING));
        verifyNoInteractions(workOrderService);
    }

    @Test
    void convert_makeAndBuyInOneRequest_isRefused() {
        SupplySuggestion make = suggestion(SupplySuggestionType.WORK_ORDER, 0, "70");
        SupplySuggestion buy = suggestion(SupplySuggestionType.PURCHASE_REQUISITION, 1, "10");
        stubOpenRun();
        stubSuggestions(make, buy);

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "v1", null, entry(make, "1"), entry(buy, "1")), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
        verifyNoInteractions(workOrderService, purchaseOrderService);
    }

    @Test
    void convert_buyWithoutSupplier_isAFieldError() {
        SupplySuggestion buy = suggestion(SupplySuggestionType.PURCHASE_REQUISITION, 1, "10");
        stubOpenRun();
        stubSuggestions(buy);

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "v1", null, entry(buy, "5")), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("supplierId");
                });
        verifyNoInteractions(purchaseOrderService);
    }

    /** A31: two BUY lines of one supplier and warehouse become one draft purchase order. */
    @Test
    void convert_buy_createsOneDraftPurchaseOrderForAllEntries_A31() {
        SupplySuggestion first = suggestion(SupplySuggestionType.PURCHASE_REQUISITION, 1, "10");
        SupplySuggestion second = suggestion(SupplySuggestionType.PURCHASE_REQUISITION, 1, "8");
        second.setNeededByDate(LocalDate.now().plusDays(3));
        stubOpenRun();
        stubSuggestions(first, second);
        UUID supplierId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID line1 = UUID.randomUUID();
        UUID line2 = UUID.randomUUID();
        when(purchaseOrderService.createFromPlanning(eq(company.getCompanyId()), eq(plant.getPlantId()),
                eq(warehouse.getWarehouseId()), eq(supplierId), any(), any(), any()))
                .thenReturn(purchaseOrderResponse(orderId, line1, line2));

        PlanningConversionResult result = service.convert(run.getMrpRunId(),
                request(0, "v1", supplierId, entry(first, "10"), entry(second, "5")), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlanningPurchaseLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(purchaseOrderService).createFromPlanning(any(), any(), any(), any(), any(), any(), lines.capture());
        assertThat(lines.getValue()).extracting(PlanningPurchaseLine::orderedQuantity)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("10"), new BigDecimal("5"));
        assertThat(lines.getValue().get(1).expectedDate()).isEqualTo(LocalDate.now().plusDays(3));
        assertThat(result.documents()).singleElement().satisfies(document -> {
            assertThat(document.type()).isEqualTo("PURCHASE_ORDER");
            assertThat(document.quantity()).isEqualByComparingTo("15");
        });
        assertThat(result.conversions()).extracting(r -> r.targetLineId()).containsExactly(line1, line2);
        verifyNoInteractions(workOrderService);
    }

    /** A30: the same key and payload return the same documents and create nothing. */
    @Test
    void convert_replayedKey_returnsTheSameDocumentsWithoutCreatingAnything_A30() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.WORK_ORDER, 0, "70");
        PlanningConversionRequest request = request(0, "v1", null, entry(suggestion, "40"));
        UUID workOrderId = UUID.randomUUID();
        SupplySuggestionConversion stored = SupplySuggestionConversion.builder()
                .conversionId(UUID.randomUUID())
                .mrpRunId(run.getMrpRunId())
                .supplySuggestionId(suggestion.getSupplySuggestionId())
                .requirementLineId(suggestion.getRequirementLine().getMrpRequirementLineId())
                .conversionBatchId(UUID.randomUUID())
                .entryNo(1)
                .quantity(new BigDecimal("40"))
                .targetType(SupplyConversionTargetType.WORK_ORDER)
                .targetId(workOrderId)
                .targetCode("MRP-AAA")
                .conversionVersion(1)
                .idempotencyKey("conv-1")
                .payloadHash(idempotency.payloadHash(request))
                .build();
        when(mrpRunRepository.lockByMrpRunId(run.getMrpRunId())).thenReturn(Optional.of(run));
        when(conversionRepository.findByMrpRunIdAndIdempotencyKeyOrderByEntryNoAsc(run.getMrpRunId(), "conv-1"))
                .thenReturn(List.of(stored));

        PlanningConversionResult result = service.convert(run.getMrpRunId(), request, "conv-1");

        assertThat(result.documents()).singleElement()
                .satisfies(document -> assertThat(document.documentId()).isEqualTo(workOrderId));
        assertThat(result.conversionVersion()).isEqualTo(1);
        verifyNoInteractions(workOrderService, purchaseOrderService, sourceVersionCalculator);
        verify(conversionRepository, never()).saveAll(any());
    }

    @Test
    void convert_replayedKeyWithAnotherPayload_isIdempotencyConflict() {
        SupplySuggestion suggestion = suggestion(SupplySuggestionType.WORK_ORDER, 0, "70");
        SupplySuggestionConversion stored = SupplySuggestionConversion.builder()
                .targetType(SupplyConversionTargetType.WORK_ORDER)
                .payloadHash(idempotency.payloadHash(request(0, "v1", null, entry(suggestion, "40"))))
                .build();
        when(mrpRunRepository.lockByMrpRunId(run.getMrpRunId())).thenReturn(Optional.of(run));
        when(conversionRepository.findByMrpRunIdAndIdempotencyKeyOrderByEntryNoAsc(run.getMrpRunId(), "conv-1"))
                .thenReturn(List.of(stored));

        assertThatThrownBy(() -> service.convert(run.getMrpRunId(),
                request(0, "v1", null, entry(suggestion, "30")), "conv-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.IDEMPOTENCY_CONFLICT));
        verifyNoInteractions(workOrderService);
    }

    // ── fixtures ────────────────────────────────────────────────────────

    private void stubOpenRun() {
        when(mrpRunRepository.lockByMrpRunId(run.getMrpRunId())).thenReturn(Optional.of(run));
        when(sourceVersionCalculator.computeCurrent(run)).thenReturn("v1");
    }

    private void stubSuggestions(SupplySuggestion... suggestions) {
        when(supplySuggestionRepository.findByRequirementLineIds(any())).thenReturn(List.of(suggestions));
        lenient().when(conversionRepository.sumBySuggestionIds(any())).thenReturn(List.of());
    }

    private void stubLedger(SupplySuggestion suggestion, String quantity) {
        when(conversionRepository.sumBySuggestionIds(any())).thenReturn(List.of(
                new SupplySuggestionConversionRepository.SuggestionQuantityProjection() {
                    @Override public UUID getSupplySuggestionId() { return suggestion.getSupplySuggestionId(); }
                    @Override public BigDecimal getQuantity() { return new BigDecimal(quantity); }
                }));
    }

    private PlanningConversionRequest request(int version, String sourceVersion, UUID supplierId,
                                              PlanningConversionEntryRequest... entries) {
        return new PlanningConversionRequest(sourceVersion, version, List.of(entries), supplierId);
    }

    private PlanningConversionEntryRequest entry(SupplySuggestion suggestion, String quantity) {
        return new PlanningConversionEntryRequest(
                suggestion.getRequirementLine().getMrpRequirementLineId(), new BigDecimal(quantity));
    }

    private SupplySuggestion suggestion(SupplySuggestionType type, int level, String quantity) {
        Item item = Item.builder().itemId(UUID.randomUUID()).company(company).code("I-" + level).name("Item")
                .type(type == SupplySuggestionType.WORK_ORDER ? ItemType.FINISHED_GOOD : ItemType.RAW_MATERIAL)
                .unit("EA").status(ItemStatus.ACTIVE).build();
        PlanningDemand demand = PlanningDemand.builder()
                .planningDemandId(UUID.randomUUID())
                .company(company).plant(plant).item(item)
                .demandType(PlanningDemandType.SALES_ORDER)
                .requiredQuantity(new BigDecimal(quantity))
                .dueDate(LocalDate.now().plusDays(10))
                .status(PlanningDemandStatus.OPEN)
                .referenceType(PlanningDemandService.REFERENCE_TYPE_SALES_ORDER_LINE)
                .referenceId(salesOrderLineId.toString())
                .build();
        MrpRequirementLine line = MrpRequirementLine.builder()
                .mrpRequirementLineId(UUID.randomUUID())
                .mrpRun(run).item(item).warehouse(warehouse).sourceDemand(demand)
                .requirementLevel(level)
                .grossRequiredQuantity(new BigDecimal(quantity))
                .netRequiredQuantity(new BigDecimal(quantity))
                .dueDate(LocalDate.now().plusDays(10))
                .requirementStatus(MrpRequirementStatus.SHORTAGE)
                .build();
        return SupplySuggestion.builder()
                .supplySuggestionId(UUID.randomUUID())
                .mrpRun(run).requirementLine(line).company(company).plant(plant)
                .warehouse(warehouse)
                .outputWarehouse(type == SupplySuggestionType.WORK_ORDER ? warehouse : null)
                .receivingWarehouse(type == SupplySuggestionType.PURCHASE_REQUISITION ? warehouse : null)
                .item(item)
                .suggestionType(type)
                .suggestedQuantity(new BigDecimal(quantity))
                .neededByDate(LocalDate.now().plusDays(10))
                .suggestedOrderDate(LocalDate.now().plusDays(7))
                .status(SupplySuggestionStatus.DRAFT)
                .build();
    }

    private SalesOrderPlanningSource source(String uncovered) {
        return new SalesOrderPlanningSource(salesOrderLineId, UUID.randomUUID(), "SO-000001", 1,
                company.getCompanyId(), plant.getPlantId(), "CONFIRMED", true, UUID.randomUUID(), "EA",
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("30"), BigDecimal.ZERO,
                new BigDecimal(uncovered), LocalDate.now().plusDays(10));
    }

    private PurchaseOrderResponse purchaseOrderResponse(UUID orderId, UUID... lineIds) {
        List<PurchaseOrderLineResponse> lines = java.util.Arrays.stream(lineIds)
                .map(id -> new PurchaseOrderLineResponse(id, null, UUID.randomUUID(), "I", "Item",
                        BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, null, null, LocalDate.now()))
                .toList();
        return new PurchaseOrderResponse(orderId, company.getCompanyId(), "C", plant.getPlantId(), "P",
                warehouse.getWarehouseId(), "W", UUID.randomUUID(), "S", "Supplier", "PO-MRP-0001", "DRAFT",
                LocalDate.now(), LocalDate.now(), null, null, null, null, lines);
    }

    private WorkOrderResponse workOrderResponse(UUID workOrderId, String workOrderNo) {
        return new WorkOrderResponse(
                workOrderId, company.getCompanyId(), plant.getPlantId(), plant.getCode(), workOrderNo,
                UUID.randomUUID(), "FG", "Product", "EA", UUID.randomUUID(), "R1", null, null, null, null, null,
                null, null, null, warehouse.getWarehouseId(), warehouse.getCode(),
                new BigDecimal("40"), BigDecimal.ZERO, new BigDecimal("40"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "DRAFT",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), null);
    }
}
