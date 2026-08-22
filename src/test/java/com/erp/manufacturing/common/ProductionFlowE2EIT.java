package com.erp.manufacturing.common;

import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSetting;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSettingStatus;
import com.erp.manufacturing.module.inventory.domain.MovementType;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.planning.domain.PlanningDemand;
import com.erp.manufacturing.module.planning.repository.PlanningDemandRepository;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.erp.manufacturing.module.user.repository.UserRepository;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.repository.WorkCenterRepository;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The end-to-end acceptance scenario of spec §11.1, run over HTTP against a real Postgres.
 *
 * <p><b>Why this class exists.</b> Every link in the chain already had unit tests, yet nothing proved
 * the links fit together: each service test builds its work order in whatever state that service
 * needs. This test is the first to walk the whole chain — confirmed sales order → planning run →
 * convert → reserve → release → issue → shop-floor report → receipt → approve → QC → idempotent
 * retry — and it found <b>three</b> defects on its first runs: the reserve/release deadlock (debt
 * #22), {@code BLOCKED} never surviving the release gate (debt #23), and the flat material-issue
 * endpoint not being replay-safe (debt #24). All three were fixed in D9/D10; this class is now their
 * regression guard, and the seam that once forced the work order status past the deadlock is gone.
 *
 * <p><b>The one {@code @SpringBootTest} in this repository.</b> {@code best-practices.md} §8.6 T1
 * otherwise forbids it. The chain crosses six modules and depends on real transactions and real
 * method security, so {@code @WebMvcTest}/{@code @DataJpaTest} cannot host it and hand-wiring the
 * services would rebuild half the container. It runs under Failsafe ({@code *IT}), so {@code mvn
 * test} is unaffected.
 *
 * <p><b>Two shapes of output.</b> The first scenario uses a lot-tracked finished good, where QC is what
 * releases the lot from {@code HOLD}. D5 added the mirror scenario for output that is <em>not</em>
 * lot-tracked, which before D5 could not pass QC at all and so could never fulfil a sales order
 * (debt #17) — that test is the acceptance evidence for the fix, and a third covers what its
 * {@code REJECTED} branch does to stock.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Production flow end-to-end (spec §11.1)")
class ProductionFlowE2EIT extends AbstractPostgresIntegrationTest {

    private static final BigDecimal ORDERED_QUANTITY = new BigDecimal("10");
    private static final BigDecimal QUANTITY_PER = new BigDecimal("2");
    /** 10 finished goods × 2 each, no scrap allowance. */
    private static final BigDecimal COMPONENT_REQUIREMENT = new BigDecimal("20.000000");
    private static final BigDecimal COMPONENT_ON_HAND = new BigDecimal("100.000000");
    /** Deliberately partial: a part-shipped order is the only way to observe the PARTIALLY_FULFILLED
     *  roll-up. (Before D11 it was also forced, because completing the work order used to close the
     *  door on receipts — debt #25.) */
    private static final BigDecimal GOOD_REPORTED = new BigDecimal("6");
    /** Shop-floor shift window. Mandatory on every production report since F7 (spec §5.1). */
    private static final String EXEC_STARTED_AT = "2026-08-03T08:00:00Z";
    private static final String EXEC_ENDED_AT = "2026-08-03T16:00:00Z";
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 8, 3);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 8, 20);
    private static final LocalDate HORIZON_END = LocalDate.of(2026, 8, 31);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WorkCenterRepository workCenterRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired InventoryLotRepository inventoryLotRepository;
    @Autowired ItemWarehouseSettingRepository itemWarehouseSettingRepository;
    @Autowired StockBalanceRepository stockBalanceRepository;
    @Autowired StockMovementRepository stockMovementRepository;
    @Autowired BomHeaderRepository bomHeaderRepository;
    @Autowired RoutingHeaderRepository routingHeaderRepository;
    @Autowired PlanningDemandRepository planningDemandRepository;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired UserRepository userRepository;
    @Autowired InventoryAvailabilityService availabilityService;

    private RequestPostProcessor asAdmin;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        asAdmin = authentication(adminAuthentication());
        fixture = seedMasterData();
    }

    @Test
    @DisplayName("Confirmed sales order reaches fulfilment only after QC releases the produced lot")
    void productionFlow_fulfilsTheSalesOrderOnlyWhenQcReleasesTheLot() throws Exception {
        long movementsBefore = stockMovementRepository.count();

        // ── 1. Confirm the sales order ⇒ independent demand for MRP ──────────────────────────────
        JsonNode order = result(post("/sales-orders/v1")
                .content(json(Map.of(
                        "companyId", fixture.companyId,
                        "plantId", fixture.plantId,
                        "orderNo", "SO-" + fixture.suffix,
                        "customerName", "E2E Customer",
                        "orderDate", ORDER_DATE.toString(),
                        "lines", List.of(Map.of(
                                "itemId", fixture.finishedGoodId,
                                "orderedQuantity", ORDERED_QUANTITY,
                                "dueDate", DUE_DATE.toString()))))), 201);
        UUID salesOrderId = uuid(order, "salesOrderId");
        UUID salesOrderLineId = uuid(order.get("lines").get(0), "salesOrderLineId");

        JsonNode confirmed = result(post("/sales-orders/v1/{id}/confirm", salesOrderId), 200);
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

        PlanningDemand demand = planningDemandRepository.findAll().stream()
                .filter(d -> salesOrderLineId.toString().equals(d.getReferenceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Confirming the order did not create a planning demand"));

        // The planner screen the demand id comes from is GET /sales-orders/planning-demands, which
        // exposes salesOrderLineId but not planningDemandId — so the id POST /planning-runs expects
        // is not reachable through the API. Read it from the repository and see debt #21.
        JsonNode demandLines = result(get("/sales-orders/v1/planning-demands")
                .param("plantId", fixture.plantId.toString())
                .param("horizonEnd", HORIZON_END.toString()), 200);
        assertThat(ids(demandLines, "salesOrderLineId")).contains(salesOrderLineId);

        // ── 2. Planning run over exactly that demand line ───────────────────────────────────────
        JsonNode run = result(post("/v1/planning-runs")
                .content(json(Map.of(
                        "companyId", fixture.companyId,
                        "plantId", fixture.plantId,
                        "warehouseId", fixture.warehouseId,
                        "horizonStartDate", ORDER_DATE.toString(),
                        "horizonEndDate", HORIZON_END.toString(),
                        "demandLineIds", List.of(demand.getPlanningDemandId())))), 201);
        UUID runId = uuid(run, "mrpRunId");
        // D4.3: `code` is derived in @PrePersist, so only a real insert proves it reaches the NOT NULL
        // column — assigning it after save() left the column null and this is what caught that.
        assertThat(run.get("code").asText())
                .isEqualTo("RUN-" + runId.toString().substring(0, 8).toUpperCase(Locale.ROOT));
        // One shortage line, one MAKE proposal, none blocked (see the suggestion assertions below).
        assertThat(run.get("shortageLines").asInt()).isEqualTo(1);
        assertThat(run.get("plannedWorkOrders").asInt()).isEqualTo(1);
        assertThat(run.get("plannedPurchaseRecommendations").asInt()).isZero();
        assertThat(run.get("blockedProposals").asInt()).isZero();

        JsonNode suggestions = result(get("/v1/planning-runs/{id}/suggestions", runId), 200)
                .get("content");
        // The component is fully covered by stock, so the finished good is the only shortage.
        assertThat(suggestions.size()).as("suggestions were %s", suggestions).isEqualTo(1);
        JsonNode suggestion = suggestions.get(0);
        assertThat(suggestion.get("supplyType").asText()).isEqualTo("MAKE");
        // WARNING, not READY: no ItemWarehouseSetting exists for this item/warehouse, so safety stock
        // and lead time fell back to the system defaults and MRP flags SYSTEM_FALLBACK_USED (B59).
        // Only BLOCKED stops a convert, so the flow continues.
        assertThat(suggestion.get("exceptionState").asText()).isEqualTo("WARNING");
        assertThat(strings(suggestion.get("messages")))
                .containsExactlyInAnyOrder("MATERIAL_SHORTAGE", "SYSTEM_FALLBACK_USED");
        // Net requirement = 10 ordered − 0 on hand − 0 safety stock.
        assertThat(new BigDecimal(suggestion.get("suggestedQuantity").asText()))
                .isEqualByComparingTo(ORDERED_QUANTITY);
        UUID suggestionId = uuid(suggestion, "supplySuggestionId");

        // ── 3. Approve + convert ⇒ work order carrying a demand allocation ──────────────────────
        result(post("/v1/supply-suggestions/{id}/approve", suggestionId)
                .content(json(Map.of("decisionNote", "approved by E2E"))), 200);
        JsonNode converted = result(post("/v1/supply-suggestions/{id}/convert-to-work-order", suggestionId)
                .content(json(Map.of("outputWarehouseId", fixture.warehouseId))), 200);
        UUID workOrderId = uuid(converted, "convertedWorkOrderId");

        JsonNode workOrder = result(get("/v1/work-orders/{id}", workOrderId), 200);
        assertThat(workOrder.get("allocations").size()).isEqualTo(1);
        JsonNode allocation = workOrder.get("allocations").get(0);
        assertThat(uuid(allocation, "salesOrderLineId")).isEqualTo(salesOrderLineId);
        assertThat(new BigDecimal(allocation.get("allocatedQuantity").asText()))
                .isEqualByComparingTo(ORDERED_QUANTITY);
        assertThat(result(get("/sales-orders/v1/{id}", salesOrderId), 200).get("status").asText())
                .isEqualTo("IN_PRODUCTION");

        // ── 4. Reserve (FEFO) — straight after convert, no release needed first (D9) ────────────
        JsonNode reservations = result(post("/v1/work-orders/{id}/reserve", workOrderId), 201);
        assertThat(reservations.size()).as("reservations were %s", reservations).isEqualTo(1);
        UUID reservationId = uuid(reservations.get(0), "reservationId");
        assertThat(new BigDecimal(reservations.get(0).get("quantity").asText()))
                .isEqualByComparingTo(COMPONENT_REQUIREMENT);
        assertThat(availableQuantity(fixture.componentId))
                .isEqualByComparingTo(COMPONENT_ON_HAND.subtract(COMPONENT_REQUIREMENT));

        // ── 5. Release now that reservation covers 100% (gate 1a / B14, happy path) ─────────────
        assertThat(result(post("/v1/work-orders/{id}/release", workOrderId), 200)
                .get("status").asText()).isEqualTo("RELEASED");

        // ── 6. Issue the reserved component ─────────────────────────────────────────────────────
        String issueKey = "E2E-ISSUE-" + fixture.suffix;
        JsonNode issue = result(post("/v1/material-issues")
                .header("Idempotency-Key", issueKey)
                .content(json(Map.of(
                        "workOrderId", workOrderId,
                        "reservationId", reservationId,
                        "quantity", COMPONENT_REQUIREMENT))), 201);
        assertThat(onHandQuantity(fixture.componentId, fixture.warehouseId))
                .isEqualByComparingTo(COMPONENT_ON_HAND.subtract(COMPONENT_REQUIREMENT));

        // ── 7. Shop floor reports good output — this, not the receipt, advances the work order ──
        String executionKey = "E2E-EXEC-" + fixture.suffix;
        JsonNode execution = result(post("/v1/work-orders/{id}/production-executions", workOrderId)
                .header("Idempotency-Key", executionKey)
                .content(json(Map.of("goodQuantity", GOOD_REPORTED,
                        "actualStartedAt", EXEC_STARTED_AT,
                        "actualEndedAt", EXEC_ENDED_AT))), 201);

        // F10 / debt H: both document numbers come from @PrePersist against a real INSERT — a code
        // assigned after save() would be missing from the statement and hit the NOT NULL column
        // (CLAUDE.md §0.12 #5). Mocked repositories never fire the callback.
        assertThat(execution.get("code").asText())
                .isEqualTo("PE-" + execution.get("productionExecutionId").asText()
                        .substring(0, 8).toUpperCase(Locale.ROOT));
        assertThat(execution.get("status").asText()).isEqualTo("POSTED");
        assertThat(issue.get("code").asText())
                .isEqualTo("MI-" + issue.get("issueId").asText().substring(0, 8).toUpperCase(Locale.ROOT));

        JsonNode afterExecution = result(get("/v1/work-orders/{id}", workOrderId), 200);
        assertThat(afterExecution.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(new BigDecimal(afterExecution.get("actualGoodQuantity").asText()))
                .isEqualByComparingTo(GOOD_REPORTED);
        assertThat(new BigDecimal(afterExecution.get("completedQuantity").asText()))
                .isEqualByComparingTo(BigDecimal.ZERO);

        // ── 8. Draft receipt must not touch inventory ───────────────────────────────────────────
        String receiptKey = "E2E-RECEIPT-" + fixture.suffix;
        JsonNode receipt = result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", receiptKey)
                .content(json(Map.of(
                        "destinationWarehouseId", fixture.warehouseId,
                        "lotNumber", "LOT-" + fixture.suffix,
                        "quantity", GOOD_REPORTED))), 201);
        UUID receiptId = uuid(receipt, "receiptId");
        assertThat(receipt.get("status").asText()).isEqualTo("DRAFT");
        assertThat(onHandQuantity(fixture.finishedGoodId, fixture.warehouseId))
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(result(post("/v1/work-orders/{id}/production-receipts/{rid}/submit",
                workOrderId, receiptId), 200).get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(onHandQuantity(fixture.finishedGoodId, fixture.warehouseId))
                .isEqualByComparingTo(BigDecimal.ZERO);

        // ── 9. Approve: stock arrives, but the lot is HOLD so nothing is usable yet ─────────────
        JsonNode approved = result(post("/v1/work-orders/{id}/production-receipts/{rid}/approve",
                workOrderId, receiptId), 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("approvedByUsername").asText()).isEqualTo("admin");

        assertThat(onHandQuantity(fixture.finishedGoodId, fixture.warehouseId))
                .isEqualByComparingTo(GOOD_REPORTED);
        // The single most valuable assertion here: on-hand rose, available did not.
        assertThat(availableQuantity(fixture.finishedGoodId)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new BigDecimal(result(get("/v1/work-orders/{id}", workOrderId), 200)
                .get("completedQuantity").asText())).isEqualByComparingTo(GOOD_REPORTED);
        // Fulfilment has not started: QC has not passed judgement yet (B62).
        assertThat(new BigDecimal(salesOrderLine(salesOrderId).get("fulfilledQuantity").asText()))
                .isEqualByComparingTo(BigDecimal.ZERO);

        // ── 10. QC releases the lot ⇒ stock becomes usable and the order is partly fulfilled ────
        JsonNode qc = result(post("/v1/work-orders/{id}/production-receipts/{rid}/qc-disposition",
                workOrderId, receiptId)
                .content(json(Map.of("result", "AVAILABLE", "reason", "Passed E2E inspection"))), 200);
        assertThat(qc.get("qcResult").asText()).isEqualTo("AVAILABLE");
        assertThat(qc.get("status").asText()).isEqualTo("APPROVED");

        assertThat(onHandQuantity(fixture.finishedGoodId, fixture.warehouseId))
                .isEqualByComparingTo(GOOD_REPORTED);
        assertThat(availableQuantity(fixture.finishedGoodId)).isEqualByComparingTo(GOOD_REPORTED);

        JsonNode fulfilledLine = salesOrderLine(salesOrderId);
        assertThat(new BigDecimal(fulfilledLine.get("fulfilledQuantity").asText()))
                .isEqualByComparingTo(GOOD_REPORTED);
        assertThat(new BigDecimal(fulfilledLine.get("openQuantity").asText()))
                .isEqualByComparingTo(ORDERED_QUANTITY.subtract(GOOD_REPORTED));
        assertThat(result(get("/sales-orders/v1/{id}", salesOrderId), 200).get("status").asText())
                .isEqualTo("PARTIALLY_FULFILLED");

        // ── 11. Replaying every mutation with its original key changes nothing ──────────────────
        long movementsAfterFlow = stockMovementRepository.count();

        // The flat issue endpoint replays the original document instead of failing on the reservation
        // it already consumed (debt #24, fixed in D10): same key ⇒ same issue id back.
        JsonNode replayedIssue = result(post("/v1/material-issues")
                .header("Idempotency-Key", issueKey)
                .content(json(Map.of(
                        "workOrderId", workOrderId,
                        "reservationId", reservationId,
                        "quantity", COMPONENT_REQUIREMENT))), 201);
        assertThat(uuid(replayedIssue, "issueId")).isEqualTo(uuid(issue, "issueId"));

        result(post("/v1/work-orders/{id}/production-executions", workOrderId)
                .header("Idempotency-Key", executionKey)
                .content(json(Map.of("goodQuantity", GOOD_REPORTED,
                        "actualStartedAt", EXEC_STARTED_AT,
                        "actualEndedAt", EXEC_ENDED_AT))), 201);
        result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", receiptKey)
                .content(json(Map.of(
                        "destinationWarehouseId", fixture.warehouseId,
                        "lotNumber", "LOT-" + fixture.suffix,
                        "quantity", GOOD_REPORTED))), 201);

        assertThat(stockMovementRepository.count()).isEqualTo(movementsAfterFlow);
        assertThat(onHandQuantity(fixture.componentId, fixture.warehouseId))
                .isEqualByComparingTo(COMPONENT_ON_HAND.subtract(COMPONENT_REQUIREMENT));
        assertThat(onHandQuantity(fixture.finishedGoodId, fixture.warehouseId))
                .isEqualByComparingTo(GOOD_REPORTED);
        assertThat(new BigDecimal(result(get("/v1/work-orders/{id}", workOrderId), 200)
                .get("actualGoodQuantity").asText())).isEqualByComparingTo(GOOD_REPORTED);
        // The whole flow writes exactly three ledger rows: ISSUE of the component, RECEIVE of the
        // output, and the LOT_STATUS_CHANGE that QC records with direction NONE (F2, B39) — a
        // traceability row that moves no quantity.
        assertThat(stockMovementRepository.count() - movementsBefore).isEqualTo(3L);
    }

    /**
     * Regression guard for the two defects this class exposed in D1 and D9 fixed (debt #22, #23).
     *
     * <p>Before D9, reserving demanded {@code RELEASED} while releasing demanded a full reservation, so
     * a work order with components could never be released through the API at all; and the
     * {@code BLOCKED} the release gate wrote was rolled back with the exception it threw from inside
     * its own {@code REQUIRES_NEW} transaction, leaving the work order {@code DRAFT}.
     *
     * <p>Neither half can be tested with mocks. The first was invisible because every unit test builds
     * a work order that is already {@code RELEASED}; the second because a mocked repository reports a
     * {@code save()} that a rollback later discards. Both need a real database — hence this test.
     */
    @Test
    @DisplayName("Release gate: BLOCKED survives the refusal, and reserving out of it leads to RELEASED")
    void releaseWithoutReservation_persistsBlocked_andReservingOutOfItAllowsRelease() throws Exception {
        UUID workOrderId = uuid(result(post("/v1/plants/{plantId}/work-orders", fixture.plantId)
                .content(json(Map.of(
                        "workOrderNo", "WO-GATE-" + fixture.suffix,
                        "productItemId", fixture.finishedGoodId,
                        "outputWarehouseId", fixture.warehouseId,
                        "plannedQuantity", ORDERED_QUANTITY))), 201), "workOrderId");

        // Release with nothing reserved: refused. The message proves the release gate itself ran,
        // rather than the "no component requirements" guard that sits before it.
        mockMvc.perform(withDefaults(post("/v1/work-orders/{id}/release", workOrderId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers
                        .containsString("component(s) not fully reserved")));

        // Debt #23: the refusal rolled back the caller's transaction, but BLOCKED must outlive it —
        // that is the whole point of B14, so planners can query what is waiting for material. Read it
        // back from the database, not from the response.
        WorkOrder blocked = workOrderRepository.findById(workOrderId).orElseThrow();
        assertThat(blocked.getStatus()).isEqualTo(WorkOrderStatus.BLOCKED);
        assertThat(blocked.getBlockedAt()).isNotNull();
        assertThat(blocked.getBlockReason()).isEqualTo("1/1 components short on reservation");

        // Debt #22: reserving is exactly what a planner does next, and BLOCKED must not stand in the way.
        JsonNode reservations = result(post("/v1/work-orders/{id}/reserve", workOrderId), 201);
        assertThat(reservations.size()).isEqualTo(1);
        assertThat(new BigDecimal(reservations.get(0).get("quantity").asText()))
                .isEqualByComparingTo(COMPONENT_REQUIREMENT);

        // With coverage complete the same call now succeeds, and the block fields are cleared.
        assertThat(result(post("/v1/work-orders/{id}/release", workOrderId), 200)
                .get("status").asText()).isEqualTo("RELEASED");
        WorkOrder released = workOrderRepository.findById(workOrderId).orElseThrow();
        assertThat(released.getBlockedAt()).isNull();
        assertThat(released.getBlockReason()).isNull();
    }

    /**
     * The acceptance evidence for debt #17, fixed in D5: a finished good that is <b>not</b>
     * lot-tracked now reaches {@code FULFILLED}.
     *
     * <p>Before D5 this order could not exist as a passing test. QC disposition refused any receipt
     * without a lot ({@code STATE_CONFLICT}, invariant B40) while fulfilment ran only from a QC
     * release (B62), so the sales order sat in {@code IN_PRODUCTION} for ever with
     * {@code fulfilledQuantity} stuck at 0 — silently, with no error anywhere to notice.
     *
     * <p><b>This is also the acceptance evidence for debt #25, fixed in D11.</b> Fulfilling the whole
     * order needs every ordered unit receipted, and reporting the full planned quantity completes the
     * work order — after which the old {@code canExecute()} gate refused any further receipt. D5 had
     * to seed a safety stock of 2 so MRP planned 12 for an order of 10, leaving the work order short
     * of completion while all 10 were receipted. That workaround is gone: the plan is now exactly the
     * 10 ordered, the work order really does reach {@code COMPLETED} mid-flow, and the receipt of the
     * last unit still goes through ({@link WorkOrder#canReceipt()}).
     */
    @Test
    @DisplayName("Debt #17: output that is not lot-tracked reaches FULFILLED through QC")
    void productionFlow_fulfilsTheSalesOrderForOutputThatIsNotLotTracked() throws Exception {
        NonLotFixture product = seedNonLotTrackedFinishedGood();

        JsonNode order = result(post("/sales-orders/v1")
                .content(json(Map.of(
                        "companyId", fixture.companyId,
                        "plantId", fixture.plantId,
                        "orderNo", "SO-NOLOT-" + fixture.suffix,
                        "customerName", "E2E Customer",
                        "orderDate", ORDER_DATE.toString(),
                        "lines", List.of(Map.of(
                                "itemId", product.itemId,
                                "orderedQuantity", ORDERED_QUANTITY,
                                "dueDate", DUE_DATE.toString()))))), 201);
        UUID salesOrderId = uuid(order, "salesOrderId");
        UUID salesOrderLineId = uuid(order.get("lines").get(0), "salesOrderLineId");
        result(post("/sales-orders/v1/{id}/confirm", salesOrderId), 200);

        PlanningDemand demand = planningDemandRepository.findAll().stream()
                .filter(d -> salesOrderLineId.toString().equals(d.getReferenceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Confirming the order did not create a planning demand"));

        JsonNode run = result(post("/v1/planning-runs")
                .content(json(Map.of(
                        "companyId", fixture.companyId,
                        "plantId", fixture.plantId,
                        "warehouseId", fixture.warehouseId,
                        "horizonStartDate", ORDER_DATE.toString(),
                        "horizonEndDate", HORIZON_END.toString(),
                        "demandLineIds", List.of(demand.getPlanningDemandId())))), 201);
        JsonNode suggestions = result(get("/v1/planning-runs/{id}/suggestions",
                uuid(run, "mrpRunId")), 200).get("content");
        assertThat(suggestions.size()).as("suggestions were %s", suggestions).isEqualTo(1);
        // Exactly what the customer ordered — no safety-stock padding to keep the work order short
        // of completion any more (debt #25).
        assertThat(new BigDecimal(suggestions.get(0).get("suggestedQuantity").asText()))
                .isEqualByComparingTo(ORDERED_QUANTITY);
        UUID suggestionId = uuid(suggestions.get(0), "supplySuggestionId");

        result(post("/v1/supply-suggestions/{id}/approve", suggestionId)
                .content(json(Map.of("decisionNote", "approved by E2E"))), 200);
        UUID workOrderId = uuid(result(post("/v1/supply-suggestions/{id}/convert-to-work-order", suggestionId)
                .content(json(Map.of("outputWarehouseId", fixture.warehouseId))), 200), "convertedWorkOrderId");

        // The work order is planned for exactly the ordered quantity, so the allocation covers the
        // whole line. (That allocation is capped by the open quantity too — covered by B63's unit
        // tests, which can arrange a surplus this scenario deliberately no longer has.)
        JsonNode allocation = result(get("/v1/work-orders/{id}", workOrderId), 200)
                .get("allocations").get(0);
        assertThat(new BigDecimal(allocation.get("allocatedQuantity").asText()))
                .isEqualByComparingTo(ORDERED_QUANTITY);

        result(post("/v1/work-orders/{id}/reserve", workOrderId), 201);
        result(post("/v1/work-orders/{id}/release", workOrderId), 200);
        result(post("/v1/work-orders/{id}/production-executions", workOrderId)
                .header("Idempotency-Key", "E2E-NOLOT-EXEC-" + fixture.suffix)
                .content(json(Map.of("goodQuantity", ORDERED_QUANTITY,
                        "actualStartedAt", EXEC_STARTED_AT,
                        "actualEndedAt", EXEC_ENDED_AT))), 201);
        // Debt #25 in one assertion: reporting the full plan completes the work order (B53), and the
        // goods still have to be warehoused afterwards. Before D11 the next three calls were 409.
        assertThat(result(get("/v1/work-orders/{id}", workOrderId), 200).get("status").asText())
                .isEqualTo("COMPLETED");

        UUID receiptId = uuid(result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", "E2E-NOLOT-RECEIPT-" + fixture.suffix)
                .content(json(Map.of(
                        "destinationWarehouseId", fixture.warehouseId,
                        "quantity", ORDERED_QUANTITY))), 201), "receiptId");
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/submit",
                workOrderId, receiptId), 200);
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/approve",
                workOrderId, receiptId), 200);

        // NON_TRACKED output has no lot row, so StockBalance.qualityHoldQuantity carries the same
        // gate: approval increases on-hand but nothing is reservable before QC.
        assertThat(onHandQuantity(product.itemId, fixture.warehouseId))
                .isEqualByComparingTo(ORDERED_QUANTITY);
        assertThat(availableQuantity(product.itemId)).isEqualByComparingTo(BigDecimal.ZERO);
        // B62 still decides fulfilment, so nothing has shipped yet.
        assertThat(new BigDecimal(salesOrderLine(salesOrderId).get("fulfilledQuantity").asText()))
                .isEqualByComparingTo(BigDecimal.ZERO);

        long movementsBeforeQc = stockMovementRepository.count();
        JsonNode qc = result(post("/v1/work-orders/{id}/production-receipts/{rid}/qc-disposition",
                workOrderId, receiptId)
                .content(json(Map.of("result", "AVAILABLE", "reason", "Passed E2E inspection"))), 200);
        assertThat(qc.get("qcResult").asText()).isEqualTo("AVAILABLE");
        assertThat(qc.get("status").asText()).isEqualTo("APPROVED");
        // No LOT_STATUS_CHANGE row: there is no lot, and AVAILABLE moves no quantity (B39 after D5).
        assertThat(stockMovementRepository.count()).isEqualTo(movementsBeforeQc);
        assertThat(availableQuantity(product.itemId)).isEqualByComparingTo(ORDERED_QUANTITY);

        // What debt #17 was about: the order finally closes.
        JsonNode fulfilledLine = salesOrderLine(salesOrderId);
        assertThat(new BigDecimal(fulfilledLine.get("fulfilledQuantity").asText()))
                .isEqualByComparingTo(ORDERED_QUANTITY);
        assertThat(new BigDecimal(fulfilledLine.get("openQuantity").asText()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result(get("/sales-orders/v1/{id}", salesOrderId), 200).get("status").asText())
                .isEqualTo("FULFILLED");
    }

    /**
     * Rejecting output that is not lot-tracked keeps it on hand and quality-held. Only a real balance
     * can prove both halves of that contract, which is why this lives here.
     */
    @Test
    @DisplayName("QC REJECTED on output without a lot keeps on-hand stock unavailable")
    void qcRejection_onOutputThatIsNotLotTracked_keepsTheGoodsOnQualityHold() throws Exception {
        NonLotFixture product = seedNonLotTrackedFinishedGood();
        UUID workOrderId = uuid(result(post("/v1/plants/{plantId}/work-orders", fixture.plantId)
                .content(json(Map.of(
                        "workOrderNo", "WO-NOLOT-REJ-" + fixture.suffix,
                        "productItemId", product.itemId,
                        "outputWarehouseId", fixture.warehouseId,
                        "plannedQuantity", ORDERED_QUANTITY))), 201), "workOrderId");
        result(post("/v1/work-orders/{id}/reserve", workOrderId), 201);
        result(post("/v1/work-orders/{id}/release", workOrderId), 200);
        result(post("/v1/work-orders/{id}/production-executions", workOrderId)
                .header("Idempotency-Key", "E2E-REJ-EXEC-" + fixture.suffix)
                .content(json(Map.of("goodQuantity", GOOD_REPORTED,
                        "actualStartedAt", EXEC_STARTED_AT,
                        "actualEndedAt", EXEC_ENDED_AT))), 201);

        UUID receiptId = uuid(result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", "E2E-REJ-RECEIPT-" + fixture.suffix)
                .content(json(Map.of(
                        "destinationWarehouseId", fixture.warehouseId,
                        "quantity", GOOD_REPORTED))), 201), "receiptId");
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/submit",
                workOrderId, receiptId), 200);
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/approve",
                workOrderId, receiptId), 200);
        assertThat(availableQuantity(product.itemId)).isEqualByComparingTo(BigDecimal.ZERO);

        result(post("/v1/work-orders/{id}/production-receipts/{rid}/qc-disposition",
                workOrderId, receiptId)
                .content(json(Map.of("result", "REJECTED", "reason", "Failed E2E inspection"))), 200);

        // Defective units remain traceable on hand, but the hold keeps them unavailable downstream.
        assertThat(onHandQuantity(product.itemId, fixture.warehouseId))
                .isEqualByComparingTo(GOOD_REPORTED);
        assertThat(availableQuantity(product.itemId)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stockMovementRepository.findAll().stream()
                .filter(m -> m.getItem().getItemId().equals(product.itemId))
                .filter(m -> m.getMovementType() == MovementType.ADJUST_OUT)
                .map(StockMovement::getQuantity)
                .toList())
                .isEmpty();
    }

    /** Closed work orders still refuse reservation — the widened rule has a floor (B13). */
    @Test
    @DisplayName("A cancelled work order cannot be reserved against")
    void reserve_onCancelledWorkOrder_isStillRefused() throws Exception {
        UUID workOrderId = uuid(result(post("/v1/plants/{plantId}/work-orders", fixture.plantId)
                .content(json(Map.of(
                        "workOrderNo", "WO-CANCELLED-" + fixture.suffix,
                        "productItemId", fixture.finishedGoodId,
                        "outputWarehouseId", fixture.warehouseId,
                        "plannedQuantity", ORDERED_QUANTITY))), 201), "workOrderId");
        // Spec §3.2 (F7): cancelling requires a reason.
        result(post("/v1/work-orders/{id}/cancel", workOrderId)
                .content(json(Map.of("reason", "Cancelled by E2E scenario"))), 200);

        mockMvc.perform(withDefaults(post("/v1/work-orders/{id}/reserve", workOrderId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        assertThat(stockBalanceRepository
                .findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(fixture.componentId, fixture.warehouseId)
                .orElseThrow().getReservedQuantity())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Authenticates as the {@code admin} seeded by {@code V2}. A real {@link UserPrincipal} rather
     * than {@code @WithMockUser} on purpose: {@code SecurityAuditorAware} only recognises that type,
     * so this is what makes {@code created_by} and {@code approvedByUsername} real values instead of
     * nulls. The {@code ROLE_ADMIN} authority short-circuits {@code PermissionGuard}, which is why no
     * role assignment or access scope has to be seeded.
     */
    private Authentication adminAuthentication() {
        User admin = userRepository.findByUsernameWithRoles("admin")
                .orElseThrow(() -> new AssertionError("V2 should have seeded the admin user"));
        UserPrincipal principal = new UserPrincipal(admin, Set.of("ADMIN"), Set.of());
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Fixture seedMasterData() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Company company = companyRepository.save(Company.builder()
                .code("E2E-" + suffix).name("E2E Company").status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder()
                .company(company).code("PL-" + suffix).name("E2E Plant")
                .status(OrganizationStatus.ACTIVE).build());
        Warehouse warehouse = warehouseRepository.save(Warehouse.builder()
                .plant(plant).code("WH-" + suffix).name("E2E Warehouse")
                .status(OrganizationStatus.ACTIVE).build());

        Item finishedGood = itemRepository.save(Item.builder()
                .company(company).code("FG-" + suffix).name("E2E Finished Good")
                .type(ItemType.FINISHED_GOOD).unit("PCS").lotTracked(true)
                .status(ItemStatus.ACTIVE).build());
        Item component = itemRepository.save(Item.builder()
                .company(company).code("CMP-" + suffix).name("E2E Component")
                .type(ItemType.RAW_MATERIAL).unit("PCS").lotTracked(false)
                .status(ItemStatus.ACTIVE).build());

        BomHeader bom = BomHeader.builder()
                .company(company).parentItem(finishedGood).revision("A")
                .status(BomStatus.ACTIVE).description("E2E BOM").build();
        bom.getLines().add(BomLine.builder()
                .bom(bom).componentItem(component).lineNo(1)
                .quantityPer(QUANTITY_PER).scrapRate(BigDecimal.ZERO).build());
        bomHeaderRepository.save(bom);

        WorkCenter workCenter = workCenterRepository.save(WorkCenter.builder()
                .plant(plant).code("WC-" + suffix).name("E2E Work Center")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build());

        RoutingHeader routing = RoutingHeader.builder()
                .company(company).item(finishedGood).code("RT-" + suffix).routingVersion("1")
                .status(RoutingStatus.ACTIVE).build();
        routing.getOperations().add(RoutingOperation.builder()
                .routing(routing).sequence(10).name("Assemble").workCenter(workCenter)
                .setupMinutes(new BigDecimal("5")).runMinutesPerUnit(new BigDecimal("1")).build());
        routingHeaderRepository.save(routing);

        stockBalanceRepository.save(StockBalance.builder()
                .item(component).warehouse(warehouse).quantity(COMPONENT_ON_HAND)
                .reservedQuantity(BigDecimal.ZERO).build());

        return new Fixture(suffix, company.getCompanyId(), plant.getPlantId(),
                warehouse.getWarehouseId(), finishedGood.getItemId(), component.getItemId());
    }

    /**
     * A second finished good on the same company/plant/warehouse that is <b>not</b> lot-tracked, with
     * its own BOM over the existing component and its own active routing (an MRP conversion requires
     * one). Seeded per test rather than in {@link #seedMasterData()} so the lot-tracked scenarios keep
     * exactly the fixture they were written against.
     *
     * <p>No safety stock: MRP plans exactly what the customer ordered. D5 had to seed 2 here to work
     * around debt #25 — see the fulfilment test.
     */
    private NonLotFixture seedNonLotTrackedFinishedGood() {
        Company company = companyRepository.findById(fixture.companyId).orElseThrow();
        Plant plant = plantRepository.findById(fixture.plantId).orElseThrow();
        Warehouse warehouse = warehouseRepository.findById(fixture.warehouseId).orElseThrow();
        Item component = itemRepository.findById(fixture.componentId).orElseThrow();

        Item finishedGood = itemRepository.save(Item.builder()
                .company(company).code("FGN-" + fixture.suffix).name("E2E Finished Good (no lot)")
                .type(ItemType.FINISHED_GOOD).unit("PCS").lotTracked(false)
                .status(ItemStatus.ACTIVE).build());

        BomHeader bom = BomHeader.builder()
                .company(company).parentItem(finishedGood).revision("A")
                .status(BomStatus.ACTIVE).description("E2E BOM (no lot)").build();
        bom.getLines().add(BomLine.builder()
                .bom(bom).componentItem(component).lineNo(1)
                .quantityPer(QUANTITY_PER).scrapRate(BigDecimal.ZERO).build());
        bomHeaderRepository.save(bom);

        WorkCenter workCenter = workCenterRepository.save(WorkCenter.builder()
                .plant(plant).code("WCN-" + fixture.suffix).name("E2E Work Center (no lot)")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build());

        RoutingHeader routing = RoutingHeader.builder()
                .company(company).item(finishedGood).code("RTN-" + fixture.suffix).routingVersion("1")
                .status(RoutingStatus.ACTIVE).build();
        routing.getOperations().add(RoutingOperation.builder()
                .routing(routing).sequence(10).name("Assemble").workCenter(workCenter)
                .setupMinutes(new BigDecimal("5")).runMinutesPerUnit(new BigDecimal("1")).build());
        routingHeaderRepository.save(routing);

        itemWarehouseSettingRepository.save(ItemWarehouseSetting.builder()
                .item(finishedGood).warehouse(warehouse).safetyStock(BigDecimal.ZERO)
                .reorderPoint(BigDecimal.ZERO).leadTimeDays(0)
                .status(ItemWarehouseSettingStatus.ACTIVE).build());

        return new NonLotFixture(finishedGood.getItemId());
    }

    private BigDecimal onHandQuantity(UUID itemId, UUID warehouseId) {
        return stockBalanceRepository.findAll().stream()
                .filter(b -> b.getItem().getItemId().equals(itemId)
                        && b.getWarehouse().getWarehouseId().equals(warehouseId))
                .map(StockBalance::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Available = on hand minus reserved, counting only lots whose status allows use (B3). */
    private BigDecimal availableQuantity(UUID itemId) {
        return availabilityService
                .getAvailableQuantities(List.of(itemId), List.of(fixture.warehouseId))
                .getOrDefault(itemId, BigDecimal.ZERO);
    }

    private JsonNode salesOrderLine(UUID salesOrderId) throws Exception {
        return result(get("/sales-orders/v1/{id}", salesOrderId), 200).get("lines").get(0);
    }

    /** Performs the request, asserts the HTTP status, and returns the {@code result} payload. */
    private JsonNode result(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        MvcResult mvcResult = mockMvc.perform(withDefaults(builder)).andReturn();
        JsonNode body = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertThat(mvcResult.getResponse().getStatus())
                .as("HTTP status for %s — body was %s", builder, body)
                .isEqualTo(expectedStatus);
        return body.get("result");
    }

    private MockHttpServletRequestBuilder withDefaults(MockHttpServletRequestBuilder builder) {
        return builder.contentType(MediaType.APPLICATION_JSON).with(asAdmin);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.get(field).asText());
    }

    private List<UUID> ids(JsonNode array, String field) {
        List<UUID> collected = new java.util.ArrayList<>();
        array.forEach(node -> collected.add(uuid(node, field)));
        return collected;
    }

    private List<String> strings(JsonNode array) {
        List<String> collected = new java.util.ArrayList<>();
        array.forEach(node -> collected.add(node.asText()));
        return collected;
    }

    private record NonLotFixture(UUID itemId) {}

    private record Fixture(String suffix,
                           UUID companyId,
                           UUID plantId,
                           UUID warehouseId,
                           UUID finishedGoodId,
                           UUID componentId) {}
}
