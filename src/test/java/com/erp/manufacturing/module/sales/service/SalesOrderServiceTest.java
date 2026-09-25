package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.PlanningDemandLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderUpdateRequest;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderPlanningDemandProjection;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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
@DisplayName("SalesOrderService tests")
class SalesOrderServiceTest {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 8, 1);

    @Mock SalesOrderRepository salesOrderRepository;
    @Mock SalesOrderLineRepository salesOrderLineRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemLookupService itemLookupService;
    @Mock PlanningDemandService planningDemandService;

    SalesOrderService service;

    @BeforeEach
    void setUp() {
        service = new SalesOrderService(
                salesOrderRepository,
                salesOrderLineRepository,
                organizationLookupService,
                itemLookupService,
                planningDemandService,
                new SalesOrderMapper());
    }

    // ── create ─────────────────────────────────────────────────────────────

    @Test
    void create_assignsSequentialLineNumbersAndStartsAtDraftWithZeroFulfilled() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID firstItemId = UUID.randomUUID();
        UUID secondItemId = UUID.randomUUID();
        Company company = company(companyId);
        Plant plant = plant(plantId, company);

        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant);
        when(salesOrderRepository.existsByCompanyCompanyIdAndOrderNo(companyId, "SO-001")).thenReturn(false);
        when(itemLookupService.getActiveItem(firstItemId)).thenReturn(item(firstItemId, company, "FG-1"));
        when(itemLookupService.getActiveItem(secondItemId)).thenReturn(item(secondItemId, company, "FG-2"));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(saveWithGeneratedIds());

        SalesOrderResponse response = service.create(new SalesOrderCreateRequest(
                companyId, plantId, "  SO-001  ", "ACME Corp", ORDER_DATE, null,
                List.of(
                        new SalesOrderLineRequest(firstItemId, new BigDecimal("25"), ORDER_DATE.plusDays(10)),
                        new SalesOrderLineRequest(secondItemId, new BigDecimal("40"), ORDER_DATE.plusDays(20)))));

        assertThat(response.status()).isEqualTo(SalesOrderStatus.DRAFT.name());
        assertThat(response.orderNo()).isEqualTo("SO-001");
        assertThat(response.lines()).hasSize(2);
        assertThat(response.lines().get(0).lineNo()).isEqualTo(1);
        assertThat(response.lines().get(0).itemSku()).isEqualTo("FG-1");
        assertThat(response.lines().get(0).orderedQuantity()).isEqualByComparingTo("25");
        assertThat(response.lines().get(0).fulfilledQuantity()).isEqualByComparingTo("0");
        assertThat(response.lines().get(0).openQuantity()).isEqualByComparingTo("25");
        assertThat(response.lines().get(1).lineNo()).isEqualTo(2);
        assertThat(response.lines().get(1).openQuantity()).isEqualByComparingTo("40");
        // Creating a DRAFT order must not produce demand — only confirm does (spec §1).
        verifyNoInteractions(planningDemandService);
    }

    @Test
    void create_duplicateOrderNoWithinCompany_fails() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        Company company = company(companyId);

        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant(plantId, company));
        when(salesOrderRepository.existsByCompanyCompanyIdAndOrderNo(companyId, "SO-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SalesOrderCreateRequest(
                companyId, plantId, "SO-001", "ACME Corp", ORDER_DATE, null,
                List.of(new SalesOrderLineRequest(UUID.randomUUID(), BigDecimal.TEN, ORDER_DATE)))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(salesOrderRepository, never()).save(any());
    }

    @Test
    void create_lineDueDateBeforeOrderDate_fails() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Company company = company(companyId);

        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant(plantId, company));
        when(salesOrderRepository.existsByCompanyCompanyIdAndOrderNo(companyId, "SO-001")).thenReturn(false);
        when(itemLookupService.getActiveItem(itemId)).thenReturn(item(itemId, company, "FG-1"));

        assertThatThrownBy(() -> service.create(new SalesOrderCreateRequest(
                companyId, plantId, "SO-001", "ACME Corp", ORDER_DATE, null,
                List.of(new SalesOrderLineRequest(itemId, BigDecimal.TEN, ORDER_DATE.minusDays(1))))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(salesOrderRepository, never()).save(any());
    }

    // ── update ─────────────────────────────────────────────────────────────

    @Test
    void update_validRequest_replacesHeaderAndLines() {
        SalesOrder order = draftOrderWithTwoLines();
        UUID salesOrderId = order.getSalesOrderId();
        Company company = order.getCompany();
        UUID newItemId = UUID.randomUUID();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));
        when(itemLookupService.getActiveItem(newItemId)).thenReturn(item(newItemId, company, "FG-3"));
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(returnFirstArgument());

        SalesOrderResponse response = service.update(salesOrderId, new SalesOrderUpdateRequest(
                1L, "New Customer", ORDER_DATE.plusDays(1), "updated note",
                List.of(new SalesOrderLineRequest(newItemId, new BigDecimal("15"), ORDER_DATE.plusDays(30)))));

        assertThat(response.customerName()).isEqualTo("New Customer");
        assertThat(response.orderDate()).isEqualTo(ORDER_DATE.plusDays(1));
        assertThat(response.note()).isEqualTo("updated note");
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).lineNo()).isEqualTo(1);
        assertThat(response.lines().get(0).itemSku()).isEqualTo("FG-3");
        assertThat(response.lines().get(0).orderedQuantity()).isEqualByComparingTo("15");
    }

    /**
     * FE contract fix (2026-08-06): {@code saveAndFlush}, not {@code save}, is what makes
     * {@code response.version()} trustworthy as the client's next {@code expectedVersion}. A plain
     * {@code save()} only queues the UPDATE — Hibernate does not bump the in-memory {@code @Version}
     * field until that UPDATE is actually flushed (normally at commit, after the response is already
     * built), so the client would be handed a version that is already one behind the persisted row.
     * The mock's {@code returnFirstArgument()} answer can't reproduce that staleness on its own, so
     * this test instead pins the collaborator call itself: reverting to {@code save} makes it red.
     */
    @Test
    void update_persistsThroughSaveAndFlush_soTheResponseVersionCanBeTrustedAsTheNextExpectedVersion() {
        SalesOrder order = draftOrderWithTwoLines();
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(returnFirstArgument());

        service.update(salesOrderId, new SalesOrderUpdateRequest(1L, "New Customer", null, null, null));

        verify(salesOrderRepository).saveAndFlush(order);
        verify(salesOrderRepository, never()).save(any());
    }

    /**
     * FE defect report 2026-08-10 ({@code B112}): a full line replace reuses {@code lineNo} 1..N, and
     * in a single Hibernate flush the child INSERTs run before the orphan-removal DELETEs — so the
     * replacement collided with {@code uk_sales_order_lines_order_line_no} and the whole PATCH failed.
     * The order of operations is the fix, so it is what this pins: the deletes must be flushed
     * <em>before</em> the first replacement line is built. A mocked repository cannot see the
     * constraint itself — that is {@code SalesOrderUpdateLinesIT}'s job (rule {@code R7}) — but it can
     * lock the sequence that keeps the two statements apart.
     */
    @Test
    void update_replacingLines_flushesTheOrphanDeletesBeforeBuildingTheReplacements() {
        SalesOrder order = draftOrderWithTwoLines();
        UUID salesOrderId = order.getSalesOrderId();
        UUID newItemId = UUID.randomUUID();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));
        when(itemLookupService.getActiveItem(newItemId))
                .thenReturn(item(newItemId, order.getCompany(), "FG-3"));
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(returnFirstArgument());

        service.update(salesOrderId, new SalesOrderUpdateRequest(1L, null, null, null,
                List.of(new SalesOrderLineRequest(newItemId, new BigDecimal("15"), ORDER_DATE.plusDays(30)))));

        InOrder inOrder = inOrder(salesOrderRepository, itemLookupService);
        inOrder.verify(salesOrderRepository).flush();
        inOrder.verify(itemLookupService).getActiveItem(newItemId);
        inOrder.verify(salesOrderRepository).saveAndFlush(order);
    }

    /**
     * The replacement lines are built after the deletes have already been flushed, so a line that is
     * only rejected inside {@code buildLine} leaves the transaction holding flushed deletes. Nothing
     * is committed — {@code saveAndFlush} is never reached and the single {@code @Transactional}
     * rolls the flushed deletes back with everything else, so the order keeps its original lines.
     */
    @Test
    void update_replacementLineFromAnotherCompany_flushesTheDeletesButNeverCommits() {
        SalesOrder order = draftOrderWithTwoLines();
        UUID salesOrderId = order.getSalesOrderId();
        UUID foreignItemId = UUID.randomUUID();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));
        when(itemLookupService.getActiveItem(foreignItemId))
                .thenReturn(item(foreignItemId, company(UUID.randomUUID()), "FG-OTHER"));

        assertThatThrownBy(() -> service.update(salesOrderId, new SalesOrderUpdateRequest(
                1L, null, null, null,
                List.of(new SalesOrderLineRequest(foreignItemId, new BigDecimal("15"),
                        ORDER_DATE.plusDays(30))))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verify(salesOrderRepository).flush();
        verify(salesOrderRepository, never()).saveAndFlush(any());
        verify(salesOrderRepository, never()).save(any());
    }

    @Test
    void update_nullLines_keepsExistingLines() {
        SalesOrder order = draftOrderWithTwoLines();
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(returnFirstArgument());

        SalesOrderResponse response = service.update(salesOrderId, new SalesOrderUpdateRequest(
                1L, "New Customer", null, null, null));

        assertThat(response.customerName()).isEqualTo("New Customer");
        assertThat(response.orderDate()).isEqualTo(ORDER_DATE);
        assertThat(response.lines()).hasSize(2);
        assertThat(response.lines().get(0).itemSku()).isEqualTo("FG-1");
        assertThat(response.lines().get(1).itemSku()).isEqualTo("FG-2");
        verifyNoInteractions(itemLookupService);
        // No replacement, so no orphan deletes to flush ahead of time either.
        verify(salesOrderRepository, never()).flush();
    }

    @Test
    void update_nonDraftOrder_throwsStateConflict() {
        SalesOrder order = draftOrderWithTwoLines();
        order.confirm();
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(salesOrderId,
                new SalesOrderUpdateRequest(1L, "New Customer", null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_staleExpectedVersion_throwsConcurrentModificationBeforeSaving() {
        SalesOrder order = draftOrderWithTwoLines();
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(salesOrderId,
                new SalesOrderUpdateRequest(99L, "New Customer", null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));

        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_orderDateMovesPastAnExistingLineDueDate_throwsAndSavesNothing() {
        SalesOrder order = draftOrderWithTwoLines();
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));

        // First line is due ORDER_DATE + 10d; pushing the order date past that must fail.
        assertThatThrownBy(() -> service.update(salesOrderId,
                new SalesOrderUpdateRequest(1L, null, ORDER_DATE.plusDays(15), null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    // ── confirm ────────────────────────────────────────────────────────────

    @Test
    void confirm_generatesOnePlanningDemandPerLine() {
        SalesOrder order = draftOrderWithTwoLines();
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(returnFirstArgument());

        SalesOrderResponse response = service.confirm(salesOrderId);

        assertThat(response.status()).isEqualTo(SalesOrderStatus.CONFIRMED.name());
        SalesOrderLine first = order.getLines().get(0);
        SalesOrderLine second = order.getLines().get(1);
        verify(planningDemandService).createFromSalesOrderLine(
                eq(order.getCompany()), eq(order.getPlant()), eq(first.getItem()),
                argThat(quantity -> quantity.compareTo(new BigDecimal("25")) == 0),
                eq(first.getDueDate()), eq(first.getSalesOrderLineId()));
        verify(planningDemandService).createFromSalesOrderLine(
                eq(order.getCompany()), eq(order.getPlant()), eq(second.getItem()),
                argThat(quantity -> quantity.compareTo(new BigDecimal("40")) == 0),
                eq(second.getDueDate()), eq(second.getSalesOrderLineId()));
        verifyNoMoreInteractions(planningDemandService);
    }

    @Test
    void confirm_alreadyConfirmed_throwsStateConflictBeforeCreatingDemand() {
        SalesOrder order = draftOrderWithTwoLines();
        order.confirm();
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirm(salesOrderId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(planningDemandService);
        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    // ── cancel ─────────────────────────────────────────────────────────────

    @Test
    void cancel_confirmedOrder_cancelsTheDemandItGenerated() {
        SalesOrder order = draftOrderWithTwoLines();
        order.confirm();
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(returnFirstArgument());
        when(planningDemandService.cancelOpenDemandsForSalesOrderLines(anyCollection())).thenReturn(2);

        SalesOrderResponse response = service.cancel(salesOrderId);

        assertThat(response.status()).isEqualTo(SalesOrderStatus.CANCELLED.name());
        verify(planningDemandService).cancelOpenDemandsForSalesOrderLines(List.of(
                order.getLines().get(0).getSalesOrderLineId(),
                order.getLines().get(1).getSalesOrderLineId()));
    }

    @Test
    void cancel_draftOrder_hasNoDemandToCancel() {
        SalesOrder order = draftOrderWithTwoLines();
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));
        when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenAnswer(returnFirstArgument());

        assertThat(service.cancel(salesOrderId).status()).isEqualTo(SalesOrderStatus.CANCELLED.name());

        verifyNoInteractions(planningDemandService);
    }

    @Test
    void cancel_orderAlreadyInProduction_throwsStateConflict() {
        SalesOrder order = draftOrderWithTwoLines();
        order.setStatus(SalesOrderStatus.IN_PRODUCTION);
        UUID salesOrderId = order.getSalesOrderId();
        when(salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(salesOrderId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verifyNoInteractions(planningDemandService);
        verify(salesOrderRepository, never()).saveAndFlush(any());
    }

    // ── planning demands ───────────────────────────────────────────────────

    @Test
    void planningDemands_readsEligibleLinesInASingleAggregateQuery() {
        UUID plantId = UUID.randomUUID();
        UUID salesOrderId = UUID.randomUUID();
        UUID salesOrderLineId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        LocalDate horizonEnd = ORDER_DATE.plusDays(30);
        LocalDate dueDate = ORDER_DATE.plusDays(10);

        SalesOrderPlanningDemandProjection projection = mock(SalesOrderPlanningDemandProjection.class);
        when(projection.getSalesOrderId()).thenReturn(salesOrderId);
        when(projection.getSalesOrderCode()).thenReturn("SO-001");
        when(projection.getSalesOrderLineId()).thenReturn(salesOrderLineId);
        when(projection.getLineNo()).thenReturn(1);
        when(projection.getItemId()).thenReturn(itemId);
        when(projection.getItemSku()).thenReturn("FG-1");
        when(projection.getItemName()).thenReturn("Finished good 1");
        when(projection.getUom()).thenReturn("EA");
        when(projection.getOpenQuantity()).thenReturn(new BigDecimal("18.000000"));
        when(projection.getDueDate()).thenReturn(dueDate);
        when(salesOrderLineRepository.findEligiblePlanningDemands(
                plantId, horizonEnd, SalesOrderService.PLANNING_ELIGIBLE_STATUSES))
                .thenReturn(List.of(projection));
        UUID planningDemandId = UUID.randomUUID();
        when(planningDemandService.findOpenDemandIdsBySalesOrderLineIds(List.of(salesOrderLineId)))
                .thenReturn(Map.of(salesOrderLineId, planningDemandId));

        List<PlanningDemandLineResponse> demands = service.planningDemands(plantId, horizonEnd);

        assertThat(demands).hasSize(1);
        PlanningDemandLineResponse demand = demands.get(0);
        assertThat(demand.salesOrderCode()).isEqualTo("SO-001");
        assertThat(demand.salesOrderLineId()).isEqualTo(salesOrderLineId);
        // Debt #21: this is the id POST /planning-runs resolves demandLineIds against. Without it the
        // planner screen and the run endpoint could not be connected at all.
        assertThat(demand.planningDemandId()).isEqualTo(planningDemandId);
        assertThat(demand.itemSku()).isEqualTo("FG-1");
        assertThat(demand.uom()).isEqualTo("EA");
        assertThat(demand.quantity()).isEqualByComparingTo("18");
        assertThat(demand.dueDate()).isEqualTo(dueDate);
        // Rule C15: N lines must cost exactly one round trip each, never one per line.
        verify(salesOrderLineRepository, times(1))
                .findEligiblePlanningDemands(any(), any(), anyCollection());
        verify(planningDemandService, times(1)).findOpenDemandIdsBySalesOrderLineIds(anyCollection());
    }

    @Test
    void planningEligibleStatuses_matchSpecSection2_1() {
        assertThat(SalesOrderService.PLANNING_ELIGIBLE_STATUSES).containsExactlyInAnyOrder(
                SalesOrderStatus.CONFIRMED,
                SalesOrderStatus.IN_PRODUCTION,
                SalesOrderStatus.PARTIALLY_FULFILLED);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private SalesOrder draftOrderWithTwoLines() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        SalesOrder order = SalesOrder.builder()
                .salesOrderId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .orderNo("SO-001")
                .customerName("ACME Corp")
                .orderDate(ORDER_DATE)
                .status(SalesOrderStatus.DRAFT)
                .build();
        order.getLines().add(line(order, 1, new BigDecimal("25"), ORDER_DATE.plusDays(10),
                item(UUID.randomUUID(), company, "FG-1")));
        order.getLines().add(line(order, 2, new BigDecimal("40"), ORDER_DATE.plusDays(20),
                item(UUID.randomUUID(), company, "FG-2")));
        order.setVersion(1L);
        return order;
    }

    private SalesOrderLine line(SalesOrder order, int lineNo, BigDecimal quantity, LocalDate dueDate, Item item) {
        return SalesOrderLine.builder()
                .salesOrderLineId(UUID.randomUUID())
                .salesOrder(order)
                .item(item)
                .lineNo(lineNo)
                .orderedQuantity(quantity)
                .fulfilledQuantity(BigDecimal.ZERO)
                .dueDate(dueDate)
                .build();
    }

    // ── list / search (DEC-01) ─────────────────────────────────────────────

    /**
     * DEC-01 is one combined term over {@code orderNo} and {@code customerName}. The two repository
     * methods differ only in that predicate, so the branch is what has to be pinned: routing a blank
     * term into the text query would silently filter every row out through {@code like '%%'} logic
     * changes, and routing a real term into the plain query would ignore the search entirely while
     * still returning a plausible-looking page.
     */
    @Test
    void list_withoutASearchTerm_usesThePlainScopedQuery() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        Company company = company(companyId);
        Plant plant = plant(plantId, company);

        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant);
        when(salesOrderRepository.search(eq(companyId), eq(plantId), isNull(), any()))
                .thenReturn(Page.empty());

        service.list(companyId, plantId, null, "   ", PageRequest.of(0, 20));

        verify(salesOrderRepository).search(eq(companyId), eq(plantId), isNull(), any());
        verify(salesOrderRepository, never()).searchWithText(any(), any(), any(), any(), any());
    }

    @Test
    void list_withASearchTerm_forwardsTheTrimmedTermAndKeepsTheStatusFilter() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        Company company = company(companyId);
        Plant plant = plant(plantId, company);

        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(plant);
        when(salesOrderRepository.searchWithText(
                eq(companyId), eq(plantId), eq(SalesOrderStatus.CONFIRMED), eq("acme"), any()))
                .thenReturn(Page.empty());

        service.list(companyId, plantId, SalesOrderStatus.CONFIRMED, "  acme  ", PageRequest.of(0, 20));

        verify(salesOrderRepository).searchWithText(
                eq(companyId), eq(plantId), eq(SalesOrderStatus.CONFIRMED), eq("acme"), any());
        verify(salesOrderRepository, never()).search(any(), any(), any(), any());
    }

    private Company company(UUID companyId) {
        return Company.builder().companyId(companyId).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
    }

    private Plant plant(UUID plantId, Company company) {
        return Plant.builder().plantId(plantId).company(company).code("P1").name("Plant 1")
                .status(OrganizationStatus.ACTIVE).build();
    }

    private Item item(UUID itemId, Company company, String code) {
        return Item.builder().itemId(itemId).company(company).code(code).name("Finished good " + code)
                .type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).build();
    }

    private org.mockito.stubbing.Answer<SalesOrder> saveWithGeneratedIds() {
        return invocation -> {
            SalesOrder order = invocation.getArgument(0);
            order.setSalesOrderId(UUID.randomUUID());
            order.getLines().forEach(line -> line.setSalesOrderLineId(UUID.randomUUID()));
            return order;
        };
    }

    private org.mockito.stubbing.Answer<SalesOrder> returnFirstArgument() {
        return invocation -> invocation.getArgument(0);
    }
}
