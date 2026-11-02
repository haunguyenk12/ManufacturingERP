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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired com.erp.manufacturing.module.sales.repository.CustomerRepository customerRepository;
    @Autowired InventoryAvailabilityService availabilityService;
    // CF-13: lock holder, revocation, flush-before-failure.
    @Autowired org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired com.erp.manufacturing.module.organization.repository.RoleRepository roleRepository;
    @Autowired com.erp.manufacturing.module.organization.repository.AccessScopeRepository accessScopeRepository;
    @Autowired com.erp.manufacturing.module.organization.repository.AccessScopeResourceRepository accessScopeResourceRepository;
    @Autowired com.erp.manufacturing.module.organization.repository.UserRoleAssignmentRepository userRoleAssignmentRepository;
    /**
     * CF-13 A40: a spy that delegates to the real repository (Boot spies a Spring Data proxy with {@code delegatesTo});
     * only that test stubs it, and Mockito resets it after each test. It is this class's own context either way — the
     * only {@code @SpringBootTest} — so no other test class pays for a second context.
     */
    @org.springframework.boot.test.mock.mockito.SpyBean
    com.erp.manufacturing.module.workorder.repository.ProductionCostRunRepository costRunRepository;

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
                        "customerId", fixture.customerId,
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
        JsonNode reservations = result(post("/v1/work-orders/{id}/reserve", workOrderId).content(versionBody(workOrderId)), 201);
        assertThat(reservations.size()).as("reservations were %s", reservations).isEqualTo(1);
        UUID reservationId = uuid(reservations.get(0), "reservationId");
        assertThat(new BigDecimal(reservations.get(0).get("quantity").asText()))
                .isEqualByComparingTo(COMPONENT_REQUIREMENT);
        assertThat(availableQuantity(fixture.componentId))
                .isEqualByComparingTo(COMPONENT_ON_HAND.subtract(COMPONENT_REQUIREMENT));

        // ── 5. Release now that reservation covers 100% (gate 1a / B14, happy path) ─────────────
        assertThat(result(post("/v1/work-orders/{id}/release", workOrderId).content(versionBody(workOrderId)), 200)
                .get("status").asText()).isEqualTo("RELEASED");

        // ── 6. Issue the reserved component ─────────────────────────────────────────────────────
        String issueKey = "E2E-ISSUE-" + fixture.suffix;
        long versionBeforeIssue = workOrderVersion(workOrderId);
        JsonNode issue = result(post("/v1/material-issues")
                .header("Idempotency-Key", issueKey)
                .content(json(Map.of(
                        "workOrderId", workOrderId,
                        "reservationId", reservationId,
                        "quantity", COMPONENT_REQUIREMENT))), 201);
        assertThat(onHandQuantity(fixture.componentId, fixture.warehouseId))
                .isEqualByComparingTo(COMPONENT_ON_HAND.subtract(COMPONENT_REQUIREMENT));
        // G8b: the first issue also moves RELEASED -> IN_PROGRESS. Both changes go out in ONE update — touching the
        // work order again after the issue's flush had already written the status moved the version on by two.
        assertThat(issue.get("workOrderVersion").asLong()).isEqualTo(versionBeforeIssue + 1);
        assertThat(workOrderVersion(workOrderId)).isEqualTo(versionBeforeIssue + 1);

        // ── 7a. G8c: the first operation takes its input from the plan, now that material was issued ──
        UUID operationId = confirmFirstOperationInput(workOrderId, ORDERED_QUANTITY);
        UUID issueLineId = uuid(issue.get("lines").get(0), "issueLineId");

        // ── 7. Shop floor reports good output — this, not the receipt, advances the work order ──
        String executionKey = "E2E-EXEC-" + fixture.suffix;
        long versionBeforeExecution = workOrderVersion(workOrderId);
        String executionBody = json(Map.of("goodQuantity", GOOD_REPORTED,
                "actualStartedAt", EXEC_STARTED_AT,
                "actualEndedAt", EXEC_ENDED_AT,
                "expectedWorkOrderVersion", versionBeforeExecution,
                "workOrderOperationId", operationId,
                "consumption", List.of(Map.of("issueLineId", issueLineId, "quantity", BigDecimal.ONE))));
        JsonNode execution = result(post("/v1/work-orders/{id}/production-executions", workOrderId)
                .header("Idempotency-Key", executionKey)
                .content(executionBody), 201);
        // G8c (B212): one report, one version step — and the consumption is recorded, not posted to stock.
        assertThat(execution.get("workOrderVersion").asLong()).isEqualTo(versionBeforeExecution + 1);
        assertThat(workOrderVersion(workOrderId)).isEqualTo(versionBeforeExecution + 1);
        assertThat(execution.get("consumption").size()).isEqualTo(1);
        assertThat(onHandQuantity(fixture.componentId, fixture.warehouseId))
                .isEqualByComparingTo(COMPONENT_ON_HAND.subtract(COMPONENT_REQUIREMENT));
        JsonNode operation = result(get("/v1/work-orders/{id}/operations", workOrderId), 200).get(0);
        assertThat(new BigDecimal(operation.get("freshRemainingQuantity").asText()))
                .isEqualByComparingTo(ORDERED_QUANTITY.subtract(GOOD_REPORTED));
        assertThat(operation.get("status").asText()).isEqualTo("IN_PROGRESS");

        // ── 7b. G8d: unused material goes back to its own source — 20 issued, 1 used ⇒ 19 unused; 4 go back ──
        BigDecimal returned = new BigDecimal("4");
        long versionBeforeReturn = workOrderVersion(workOrderId);
        String returnKey = "E2E-RETURN-" + fixture.suffix;
        String returnBody = json(Map.of("expectedWorkOrderVersion", versionBeforeReturn,
                "reason", "Unused offcut back to stock",
                "lines", List.of(Map.of("sourceIssueLineId", issueLineId, "quantity", returned))));
        JsonNode materialReturn = result(post("/v1/work-orders/{id}/material-returns", workOrderId)
                .header("Idempotency-Key", returnKey).content(returnBody), 201);
        assertThat(materialReturn.get("workOrderVersion").asLong()).isEqualTo(versionBeforeReturn + 1);
        assertThat(workOrderVersion(workOrderId)).isEqualTo(versionBeforeReturn + 1);
        BigDecimal componentAfterReturn = COMPONENT_ON_HAND.subtract(COMPONENT_REQUIREMENT).add(returned);
        assertThat(onHandQuantity(fixture.componentId, fixture.warehouseId)).isEqualByComparingTo(componentAfterReturn);
        // R9: the same key and body replays the first return — no second receipt into stock.
        JsonNode replayedReturn = result(post("/v1/work-orders/{id}/material-returns", workOrderId)
                .header("Idempotency-Key", returnKey).content(returnBody), 201);
        assertThat(replayedReturn.get("materialReturnId").asText()).isEqualTo(materialReturn.get("materialReturnId").asText());
        assertThat(onHandQuantity(fixture.componentId, fixture.warehouseId)).isEqualByComparingTo(componentAfterReturn);
        JsonNode material = result(get("/v1/work-orders/{id}/wip", workOrderId).param("section", "MATERIALS"), 200)
                .get("materials").get(0);
        assertThat(new BigDecimal(material.get("issuedQuantity").asText())).isEqualByComparingTo(COMPONENT_REQUIREMENT);
        assertThat(new BigDecimal(material.get("consumedQuantity").asText())).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(new BigDecimal(material.get("returnedQuantity").asText())).isEqualByComparingTo(returned);
        assertThat(new BigDecimal(material.get("unusedQuantity").asText())).isEqualByComparingTo("15");

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
        long versionBeforeReceipt = workOrderVersion(workOrderId);
        String receiptBody = json(Map.of(
                "destinationWarehouseId", fixture.warehouseId,
                "lotNumber", "LOT-" + fixture.suffix,
                "quantity", GOOD_REPORTED,
                "expectedWorkOrderVersion", versionBeforeReceipt,
                "receivedOn", plantToday().toString()));
        JsonNode receipt = result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", receiptKey)
                .content(receiptBody), 201);
        UUID receiptId = uuid(receipt, "receiptId");
        // G9a (FE Q12): a receipt claims output of the work order, so its version moves on — exactly once.
        assertThat(receipt.get("workOrderVersion").asLong()).isEqualTo(versionBeforeReceipt + 1);
        assertThat(workOrderVersion(workOrderId)).isEqualTo(versionBeforeReceipt + 1);
        assertThat(receipt.get("status").asText()).isEqualTo("DRAFT");
        assertThat(onHandQuantity(fixture.finishedGoodId, fixture.warehouseId))
                .isEqualByComparingTo(BigDecimal.ZERO);
        // G9b (D3): the server names the report the receipt draws from — the one final-good execution above.
        assertThat(receipt.get("outputEvidenceKnown").asBoolean()).isTrue();
        assertThat(receipt.get("contributions")).hasSize(1);
        assertThat(receipt.get("contributions").get(0).get("executionId").asText())
                .isEqualTo(execution.get("productionExecutionId").asText());
        assertThat(new BigDecimal(receipt.get("contributions").get(0).get("quantity").asText()))
                .isEqualByComparingTo(GOOD_REPORTED);

        JsonNode submitted = result(post("/v1/work-orders/{id}/production-receipts/{rid}/submit",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200);
        assertThat(submitted.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(onHandQuantity(fixture.finishedGoodId, fixture.warehouseId))
                .isEqualByComparingTo(BigDecimal.ZERO);

        // ── 9. Approve: stock arrives, but the lot is HOLD so nothing is usable yet ─────────────
        long versionBeforeApproval = workOrderVersion(workOrderId);
        long receiptVersionBeforeApproval = submitted.get("version").asLong();
        JsonNode approved = result(post("/v1/work-orders/{id}/production-receipts/{rid}/approve",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200);
        // The +2 trap of G8b: approval raises completedQuantity and moves stock, yet the version moves once.
        assertThat(approved.get("workOrderVersion").asLong()).isEqualTo(versionBeforeApproval + 1);
        assertThat(workOrderVersion(workOrderId)).isEqualTo(versionBeforeApproval + 1);
        // ...and so does the receipt's own version: one command, one step, in the response and read back.
        assertThat(approved.get("version").asLong()).isEqualTo(receiptVersionBeforeApproval + 1);
        assertThat(result(get("/v1/work-orders/{id}/production-receipts/{rid}", workOrderId, receiptId), 200)
                .get("version").asLong()).isEqualTo(receiptVersionBeforeApproval + 1);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("approvedByUsername").asText()).isEqualTo("admin");
        // G9b (FE R13): the source of the receipt — its report, and the work order's material with the return.
        JsonNode source = result(get("/v1/work-orders/{id}/production-receipts/{rid}/source", workOrderId, receiptId), 200);
        assertThat(source.get("outputEvidenceKnown").asBoolean()).isTrue();
        assertThat(source.get("materialAttribution").asText()).isEqualTo("WORK_ORDER_LEVEL");
        assertThat(new BigDecimal(source.get("materials").get(0).get("returnedQuantity").asText()))
                .isEqualByComparingTo(returned);

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
        // G9c (D2): the old whole-receipt endpoint writes the same decision ledger the partial QC uses.
        assertThat(qc.get("qualityState").asText()).isEqualTo("ACCEPTED");
        assertThat(new BigDecimal(qc.get("pendingQcQuantity").asText())).isEqualByComparingTo(BigDecimal.ZERO);
        JsonNode decisions = result(get("/v1/work-orders/{id}/production-receipts/{rid}/qc-decisions", workOrderId, receiptId), 200);
        assertThat(decisions.get("content")).hasSize(1);
        assertThat(new BigDecimal(decisions.get("content").get(0).get("acceptedQuantity").asText()))
                .isEqualByComparingTo(GOOD_REPORTED);

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
                .content(executionBody), 201);
        result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", receiptKey)
                .content(receiptBody), 201);

        assertThat(stockMovementRepository.count()).isEqualTo(movementsAfterFlow);
        assertThat(onHandQuantity(fixture.componentId, fixture.warehouseId)).isEqualByComparingTo(componentAfterReturn);
        assertThat(onHandQuantity(fixture.finishedGoodId, fixture.warehouseId))
                .isEqualByComparingTo(GOOD_REPORTED);
        assertThat(new BigDecimal(result(get("/v1/work-orders/{id}", workOrderId), 200)
                .get("actualGoodQuantity").asText())).isEqualByComparingTo(GOOD_REPORTED);
        // The whole flow writes exactly four ledger rows: ISSUE of the component, RECEIVE of the returned
        // material (G8d, once despite the replay), RECEIVE of the output, and the LOT_STATUS_CHANGE that QC
        // records with direction NONE (F2, B39) — a traceability row that moves no quantity.
        assertThat(stockMovementRepository.count() - movementsBefore).isEqualTo(4L);
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
        mockMvc.perform(withDefaults(post("/v1/work-orders/{id}/release", workOrderId).content(versionBody(workOrderId))))
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
        JsonNode reservations = result(post("/v1/work-orders/{id}/reserve", workOrderId).content(versionBody(workOrderId)), 201);
        assertThat(reservations.size()).isEqualTo(1);
        assertThat(new BigDecimal(reservations.get(0).get("quantity").asText()))
                .isEqualByComparingTo(COMPONENT_REQUIREMENT);

        // With coverage complete the same call now succeeds, and the block fields are cleared.
        assertThat(result(post("/v1/work-orders/{id}/release", workOrderId).content(versionBody(workOrderId)), 200)
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
                        "customerId", fixture.customerId,
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

        JsonNode nonLotReservations = result(post("/v1/work-orders/{id}/reserve", workOrderId)
                .content(versionBody(workOrderId)), 201);
        result(post("/v1/work-orders/{id}/release", workOrderId).content(versionBody(workOrderId)), 200);
        // G8c: an MRP work order carries its routing, so its single operation needs material issued and input confirmed.
        result(post("/v1/material-issues")
                .header("Idempotency-Key", "E2E-NOLOT-ISSUE-" + fixture.suffix)
                .content(json(Map.of(
                        "workOrderId", workOrderId,
                        "reservationId", uuid(nonLotReservations.get(0), "reservationId"),
                        "quantity", new BigDecimal(nonLotReservations.get(0).get("quantity").asText())))), 201);
        UUID nonLotOperationId = confirmFirstOperationInput(workOrderId, ORDERED_QUANTITY);
        result(post("/v1/work-orders/{id}/production-executions", workOrderId)
                .header("Idempotency-Key", "E2E-NOLOT-EXEC-" + fixture.suffix)
                .content(json(Map.of("goodQuantity", ORDERED_QUANTITY,
                        "actualStartedAt", EXEC_STARTED_AT,
                        "actualEndedAt", EXEC_ENDED_AT,
                        "expectedWorkOrderVersion", workOrderVersion(workOrderId),
                        "workOrderOperationId", nonLotOperationId))), 201);
        // Debt #25 in one assertion: reporting the full plan completes the work order (B53), and the
        // goods still have to be warehoused afterwards. Before D11 the next three calls were 409.
        assertThat(result(get("/v1/work-orders/{id}", workOrderId), 200).get("status").asText())
                .isEqualTo("COMPLETED");

        UUID receiptId = uuid(result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", "E2E-NOLOT-RECEIPT-" + fixture.suffix)
                .content(json(Map.of(
                        "destinationWarehouseId", fixture.warehouseId,
                        "quantity", ORDERED_QUANTITY,
                        "expectedWorkOrderVersion", workOrderVersion(workOrderId),
                        "receivedOn", plantToday().toString()))), 201), "receiptId");
        long receiptVersionBeforeApproval = result(post("/v1/work-orders/{id}/production-receipts/{rid}/submit",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200).get("version").asLong();
        JsonNode approved = result(post("/v1/work-orders/{id}/production-receipts/{rid}/approve",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200);
        // One command, one version step — also when the output has no lot to carry the hold.
        assertThat(approved.get("version").asLong()).isEqualTo(receiptVersionBeforeApproval + 1);
        assertThat(result(get("/v1/work-orders/{id}/production-receipts/{rid}", workOrderId, receiptId), 200)
                .get("version").asLong()).isEqualTo(receiptVersionBeforeApproval + 1);

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
        JsonNode rejectReservations = result(post("/v1/work-orders/{id}/reserve", workOrderId)
                .content(versionBody(workOrderId)), 201);
        result(post("/v1/work-orders/{id}/release", workOrderId).content(versionBody(workOrderId)), 200);
        // G8c: a manual work order snapshots the active routing too, so its operation needs issue + input first.
        result(post("/v1/material-issues")
                .header("Idempotency-Key", "E2E-REJ-ISSUE-" + fixture.suffix)
                .content(json(Map.of(
                        "workOrderId", workOrderId,
                        "reservationId", uuid(rejectReservations.get(0), "reservationId"),
                        "quantity", new BigDecimal(rejectReservations.get(0).get("quantity").asText())))), 201);
        UUID rejectOperationId = confirmFirstOperationInput(workOrderId, GOOD_REPORTED);
        result(post("/v1/work-orders/{id}/production-executions", workOrderId)
                .header("Idempotency-Key", "E2E-REJ-EXEC-" + fixture.suffix)
                .content(json(Map.of("goodQuantity", GOOD_REPORTED,
                        "actualStartedAt", EXEC_STARTED_AT,
                        "actualEndedAt", EXEC_ENDED_AT,
                        "expectedWorkOrderVersion", workOrderVersion(workOrderId),
                        "workOrderOperationId", rejectOperationId))), 201);

        UUID receiptId = uuid(result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", "E2E-REJ-RECEIPT-" + fixture.suffix)
                .content(json(Map.of(
                        "destinationWarehouseId", fixture.warehouseId,
                        "quantity", GOOD_REPORTED,
                        "expectedWorkOrderVersion", workOrderVersion(workOrderId),
                        "receivedOn", plantToday().toString()))), 201), "receiptId");
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/submit",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200);
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/approve",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200);
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

    /**
     * G11: a work order released with prices and rates gets a cost baseline in the release transaction; the old close is
     * refused for it (D5); a preview changes nothing; finalize-and-close re-calculates, writes the final snapshot and the
     * value-only entry and closes, all at once — and of two finalizes racing on the same versions only one commits (A35).
     */
    @Test
    @DisplayName("G11: baseline at release, preview, atomic finalize-and-close")
    void productionCost_finalizeAndClose_isAtomicAndTheOldCloseIsBlocked() throws Exception {
        NonLotFixture product = seedNonLotTrackedFinishedGood();
        result(put("/v1/companies/{c}/items/{i}/standard-cost", fixture.companyId, fixture.componentId)
                .content(json(Map.of("materialCost", 1000, "laborCost", 0, "overheadCost", 0))), 200);
        JsonNode rate = result(put("/v1/plants/{p}/production-cost-rates/{w}", fixture.plantId, product.workCenterId)
                .header("Idempotency-Key", "E2E-G11-RATE-" + fixture.suffix)
                .content(json(Map.of("expectedVersion", 0, "laborRate", 6000, "overheadRate", 3000,
                        "reason", "E2E operating rate"))), 200);
        assertThat(rate.get("rateVersion").asInt()).isEqualTo(1);

        UUID workOrderId = uuid(result(post("/v1/plants/{plantId}/work-orders", fixture.plantId)
                .content(json(Map.of(
                        "workOrderNo", "WO-G11-" + fixture.suffix,
                        "productItemId", product.itemId,
                        "outputWarehouseId", fixture.warehouseId,
                        "plannedQuantity", ORDERED_QUANTITY))), 201), "workOrderId");
        JsonNode reservations = result(post("/v1/work-orders/{id}/reserve", workOrderId)
                .content(versionBody(workOrderId)), 201);
        result(post("/v1/work-orders/{id}/release", workOrderId).content(versionBody(workOrderId)), 200);

        // A rate saved after release never reaches the captured baseline (B256, FE A03).
        result(put("/v1/plants/{p}/production-cost-rates/{w}", fixture.plantId, product.workCenterId)
                .header("Idempotency-Key", "E2E-G11-RATE2-" + fixture.suffix)
                .content(json(Map.of("expectedVersion", 1, "laborRate", 9000, "overheadRate", 9000,
                        "reason", "Rate raised after release"))), 200);
        JsonNode baseline = result(get("/v1/work-orders/{id}/cost-baseline", workOrderId), 200).get("baseline");
        assertThat(new BigDecimal(baseline.get("materials").get(0).get("unitCost").asText())).isEqualByComparingTo("1000");
        JsonNode plannedOperation = baseline.get("operations").get(0);
        assertThat(new BigDecimal(plannedOperation.get("plannedMinutes").asText())).isEqualByComparingTo("15");
        assertThat(new BigDecimal(plannedOperation.get("laborRate").asText())).isEqualByComparingTo("6000");
        assertThat(new BigDecimal(baseline.get("planned").get("total").asText())).isEqualByComparingTo("22250");

        JsonNode issue = result(post("/v1/material-issues")
                .header("Idempotency-Key", "E2E-G11-ISSUE-" + fixture.suffix)
                .content(json(Map.of(
                        "workOrderId", workOrderId,
                        "reservationId", uuid(reservations.get(0), "reservationId"),
                        "quantity", new BigDecimal(reservations.get(0).get("quantity").asText())))), 201);
        UUID issueLineId = uuid(issue.get("lines").get(0), "issueLineId");
        UUID operationId = confirmFirstOperationInput(workOrderId, ORDERED_QUANTITY);
        result(post("/v1/work-orders/{id}/production-executions", workOrderId)
                .header("Idempotency-Key", "E2E-G11-EXEC-" + fixture.suffix)
                .content(json(Map.of("goodQuantity", ORDERED_QUANTITY,
                        "actualStartedAt", EXEC_STARTED_AT,
                        "actualEndedAt", EXEC_ENDED_AT,
                        "expectedWorkOrderVersion", workOrderVersion(workOrderId),
                        "workOrderOperationId", operationId,
                        "consumption", List.of(Map.of("issueLineId", issueLineId,
                                "quantity", ORDERED_QUANTITY.multiply(QUANTITY_PER)))))), 201);
        result(post("/v1/work-orders/{id}/operations/{op}/finish", workOrderId, operationId)
                .content(versionBody(workOrderId)), 200);
        UUID receiptId = uuid(result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", "E2E-G11-RECEIPT-" + fixture.suffix)
                .content(json(Map.of(
                        "destinationWarehouseId", fixture.warehouseId,
                        "quantity", ORDERED_QUANTITY,
                        "expectedWorkOrderVersion", workOrderVersion(workOrderId),
                        "receivedOn", plantToday().toString()))), 201), "receiptId");
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/submit",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200);
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/approve",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200);
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/qc-disposition", workOrderId, receiptId)
                .content(json(Map.of("result", "AVAILABLE", "reason", "Passed E2E inspection"))), 200);

        // D5: the P6 close would skip the checklist, so a work order with a baseline cannot use it.
        mockMvc.perform(withDefaults(post("/v1/work-orders/{id}/close", workOrderId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLOSE_BLOCKED"));

        JsonNode checklist = result(get("/v1/work-orders/{id}/cost-checklist", workOrderId), 200);
        assertThat(checklist.get("readiness").asText()).as("checklist %s", checklist).isEqualTo("BLOCKED");
        assertThat(strings(checklist.get("checks").get(10).get("reasonCodes"))).containsExactly("NO_PREVIEW");
        String sourceVersion = checklist.get("sourceVersion").asText();
        long versionBeforePreview = workOrderVersion(workOrderId);
        long movementsBeforePreview = stockMovementRepository.count();

        JsonNode run = result(post("/v1/work-orders/{id}/cost-runs", workOrderId)
                .header("Idempotency-Key", "E2E-G11-RUN-" + fixture.suffix)
                .content(json(Map.of("expectedVersion", versionBeforePreview,
                        "expectedSourceVersion", sourceVersion))), 200);
        UUID runId = uuid(run, "runId");
        // A preview changes nothing but itself (FE R16, A19).
        assertThat(workOrderVersion(workOrderId)).isEqualTo(versionBeforePreview);
        assertThat(stockMovementRepository.count()).isEqualTo(movementsBeforePreview);
        JsonNode overview = result(get("/v1/work-orders/{id}/production-costs", workOrderId), 200);
        assertThat(overview.get("readiness").asText()).as("overview %s", overview).isEqualTo("READY");
        assertThat(new BigDecimal(overview.get("data").get("actual").get("total").asText()))
                .isEqualByComparingTo("92000");
        assertThat(new BigDecimal(overview.get("data").get("unitCost").asText())).isEqualByComparingTo("9200");

        mockMvc.perform(withDefaults(post("/v1/work-orders/{id}/finalize-cost-and-close", workOrderId)
                        .header("Idempotency-Key", "E2E-G11-STALE-" + fixture.suffix)
                        .content(json(finalizeBody(versionBeforePreview, "cs1-not-the-current-one", runId)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COST_SOURCE_STALE"));

        // Two finalizes on the same versions with different keys: the row lock serialises them, one commits.
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<MvcResult>> racing = new java.util.ArrayList<>();
        for (String key : List.of("E2E-G11-FIN-A-", "E2E-G11-FIN-B-")) {
            String body = json(finalizeBody(versionBeforePreview, sourceVersion, runId));
            racing.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(withDefaults(post("/v1/work-orders/{id}/finalize-cost-and-close", workOrderId)
                        .header("Idempotency-Key", key + fixture.suffix).content(body))).andReturn();
            }));
        }
        start.countDown();
        List<Integer> statuses = new java.util.ArrayList<>();
        for (java.util.concurrent.Future<MvcResult> future : racing) {
            statuses.add(future.get(60, java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus());
        }
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        JsonNode closedRun = result(get("/v1/work-orders/{id}/cost-runs/{r}", workOrderId, runId), 200);
        assertThat(closedRun.get("status").asText()).isEqualTo("CLOSED");
        assertThat(closedRun.get("readiness").asText()).isEqualTo("CLOSED");
        assertThat(closedRun.get("closed").get("closeReason").asText()).isEqualTo("Cost reconciled by E2E");
        assertThat(workOrderVersion(workOrderId)).isEqualTo(versionBeforePreview + 1);
        JsonNode entries = result(get("/v1/work-orders/{id}/cost-runs/{r}/adjustments", workOrderId, runId), 200)
                .get("content");
        assertThat(entries.size()).isEqualTo(1);
        assertThat(new BigDecimal(entries.get(0).get("quantity").asText())).isEqualByComparingTo("0");
        assertThat(new BigDecimal(entries.get(0).get("stockValueDelta").asText())).isEqualByComparingTo("92000");
        assertThat(result(get("/v1/work-orders/{id}/cost-runs", workOrderId), 200).get("content").get(0)
                .get("state").asText()).isEqualTo("FINALIZED");
        // Closing never moved stock: on hand is still the 10 received (FE §3.6).
        assertThat(onHandQuantity(product.itemId, fixture.warehouseId)).isEqualByComparingTo(ORDERED_QUANTITY);
    }

    /**
     * CF-13 / FE A39: the closer's assignment is revoked while its finalize waits for the work order row lock. The
     * {@code @PreAuthorize} check passed before the wait, so only the re-check under the lock can stop it: 403, and
     * nothing of the finalize lands — run still a preview, no value entry, the work order neither closed nor moved on,
     * no idempotency record. The same request by an admin then closes it, so the work order was ready: the 403 was the
     * revocation and nothing else.
     */
    @Test
    @DisplayName("CF-13 A39: a close permission revoked while waiting for the lock is refused, nothing written")
    void productionCost_permissionRevokedWhileWaitingForTheLock_isRefusedAndNothingIsWritten() throws Exception {
        Finalizable ready = prepareFinalizable("A39");
        CloserGrant closer = closerOnPlant(fixture.plantId);
        String key = "E2E-A39-FIN-" + fixture.suffix;
        String body = json(finalizeBody(ready.version(), ready.sourceVersion(), ready.runId()));
        long movementsBefore = stockMovementRepository.count();

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch lockHeld = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseLock = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Future<Object> holder = pool.submit(() -> transactionTemplate.execute(status -> {
            jdbcTemplate.queryForList("select 1 from work_orders where work_order_id = ? for update", ready.workOrderId());
            lockHeld.countDown();
            awaitLatch(releaseLock);
            return null;
        }));
        awaitLatch(lockHeld);
        java.util.concurrent.Future<MvcResult> finalize = pool.submit(() -> mockMvc.perform(
                post("/v1/work-orders/{id}/finalize-cost-and-close", ready.workOrderId())
                        .contentType(MediaType.APPLICATION_JSON).with(authentication(closer.authentication()))
                        .header("Idempotency-Key", key).content(body)).andReturn());

        awaitBlockedOn("%from work_orders where work_order_id%for update%");
        assertThat(jdbcTemplate.update("update user_role_assignments set status = 'INACTIVE' where assignment_id = ?",
                closer.assignmentId())).isEqualTo(1);
        releaseLock.countDown();
        holder.get(60, java.util.concurrent.TimeUnit.SECONDS);
        MvcResult refused = finalize.get(60, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(refused.getResponse().getStatus()).as(refused.getResponse().getContentAsString()).isEqualTo(403);
        assertThat(objectMapper.readTree(refused.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("PERMISSION_DENIED");
        assertNothingFinalized(ready, key, movementsBefore);

        // Control: the same finalize by someone still allowed goes through.
        result(post("/v1/work-orders/{id}/finalize-cost-and-close", ready.workOrderId())
                .header("Idempotency-Key", "E2E-A39-ADMIN-" + fixture.suffix).content(body), 200);
        assertThat(workOrderRepository.findById(ready.workOrderId()).orElseThrow().getStatus())
                .isEqualTo(WorkOrderStatus.CLOSED);
    }

    /**
     * CF-13 / FE A40: a failure after every write of the finalize has reached the database (value entries saved, the
     * run turned FINALIZED, all flushed) rolls everything back — run, entries, work order, idempotency record, stock.
     * The retry with the same key then finalizes exactly once, and a further retry is a replay.
     */
    @Test
    @DisplayName("CF-13 A40: a failure midway rolls the whole finalize back; the retry finalizes once")
    void productionCost_failureMidwayRollsEverythingBack_andTheRetryFinalizesOnce() throws Exception {
        Finalizable ready = prepareFinalizable("A40");
        String key = "E2E-A40-FIN-" + fixture.suffix;
        String body = json(finalizeBody(ready.version(), ready.sourceVersion(), ready.runId()));
        long movementsBefore = stockMovementRepository.count();
        org.mockito.stubbing.Answer<?> realSave = org.mockito.Mockito.mockingDetails(costRunRepository)
                .getMockCreationSettings().getDefaultAnswer();
        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicLong entriesWrittenBeforeFailure = new java.util.concurrent.atomic.AtomicLong(-1);
        org.mockito.Mockito.doAnswer(invocation -> {
            Object saved = realSave.answer(invocation);
            com.erp.manufacturing.module.workorder.domain.ProductionCostRun run = invocation.getArgument(0);
            if (run.isFinalized() && failed.compareAndSet(false, true)) {
                entityManager.flush();
                entriesWrittenBeforeFailure.set(((Number) entityManager.createNativeQuery(
                                "select count(*) from production_cost_adjustments where run_id = :run")
                        .setParameter("run", run.getRunId()).getSingleResult()).longValue());
                throw new IllegalStateException("CF-13 injected failure after the finalize writes were flushed");
            }
            return saved;
        }).when(costRunRepository).save(org.mockito.ArgumentMatchers.any(
                com.erp.manufacturing.module.workorder.domain.ProductionCostRun.class));

        mockMvc.perform(withDefaults(post("/v1/work-orders/{id}/finalize-cost-and-close", ready.workOrderId())
                        .header("Idempotency-Key", key).content(body)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
        assertThat(failed).isTrue();
        assertThat(entriesWrittenBeforeFailure.get()).as("value entries were in the database before the failure")
                .isEqualTo(1);
        assertNothingFinalized(ready, key, movementsBefore);

        JsonNode retried = result(post("/v1/work-orders/{id}/finalize-cost-and-close", ready.workOrderId())
                .header("Idempotency-Key", key).content(body), 200);
        assertThat(uuid(retried, "runId")).isEqualTo(ready.runId());
        assertThat(retried.get("workOrderVersion").asLong()).isEqualTo(ready.version() + 1);
        JsonNode replayed = result(post("/v1/work-orders/{id}/finalize-cost-and-close", ready.workOrderId())
                .header("Idempotency-Key", key).content(body), 200);
        assertThat(replayed.get("workOrderVersion").asLong()).isEqualTo(ready.version() + 1);

        Map<String, Object> run = jdbcTemplate.queryForMap(
                "select state, run_version from production_cost_runs where run_id = ?", ready.runId());
        assertThat(run.get("state")).isEqualTo("FINALIZED");
        assertThat(((Number) run.get("run_version")).intValue()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from production_cost_runs where work_order_id = ? and state = 'FINALIZED'",
                Integer.class, ready.workOrderId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from production_cost_adjustments where work_order_id = ?",
                Integer.class, ready.workOrderId())).isEqualTo(1);
        WorkOrder closed = workOrderRepository.findById(ready.workOrderId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(closed.getVersion()).isEqualTo(ready.version() + 1);
        assertThat(stockMovementRepository.count()).isEqualTo(movementsBefore);
    }

    /** The finalize left no trace: preview run, no value entry, work order untouched, no idempotency record, no stock. */
    private void assertNothingFinalized(Finalizable ready, String key, long movementsBefore) {
        Map<String, Object> run = jdbcTemplate.queryForMap(
                "select state, run_version, finalized_at from production_cost_runs where run_id = ?", ready.runId());
        assertThat(run.get("state")).isEqualTo("PREVIEW");
        assertThat(((Number) run.get("run_version")).intValue()).isEqualTo(1);
        assertThat(run.get("finalized_at")).isNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from production_cost_adjustments where work_order_id = ?",
                Integer.class, ready.workOrderId())).isZero();
        Map<String, Object> workOrder = jdbcTemplate.queryForMap(
                "select status, version from work_orders where work_order_id = ?", ready.workOrderId());
        assertThat(workOrder.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) workOrder.get("version")).longValue()).isEqualTo(ready.version());
        assertThat(jdbcTemplate.queryForObject("select count(*) from work_order_idempotency_records where idempotency_key = ?",
                Integer.class, key)).isZero();
        assertThat(stockMovementRepository.count()).isEqualTo(movementsBefore);
    }

    /**
     * A work order through the whole G11 flow up to a preview run: rate and price, baseline at release, issue, report,
     * receipt approved and QC'd — ready for finalize-and-close (the checklist is READY but for the preview it names).
     */
    private Finalizable prepareFinalizable(String tag) throws Exception {
        String prefix = "E2E-" + tag + "-";
        NonLotFixture product = seedNonLotTrackedFinishedGood();
        result(put("/v1/companies/{c}/items/{i}/standard-cost", fixture.companyId, fixture.componentId)
                .content(json(Map.of("materialCost", 1000, "laborCost", 0, "overheadCost", 0))), 200);
        result(put("/v1/plants/{p}/production-cost-rates/{w}", fixture.plantId, product.workCenterId)
                .header("Idempotency-Key", prefix + "RATE-" + fixture.suffix)
                .content(json(Map.of("expectedVersion", 0, "laborRate", 6000, "overheadRate", 3000,
                        "reason", "E2E operating rate"))), 200);
        UUID workOrderId = uuid(result(post("/v1/plants/{plantId}/work-orders", fixture.plantId)
                .content(json(Map.of(
                        "workOrderNo", "WO-" + tag + "-" + fixture.suffix,
                        "productItemId", product.itemId,
                        "outputWarehouseId", fixture.warehouseId,
                        "plannedQuantity", ORDERED_QUANTITY))), 201), "workOrderId");
        JsonNode reservations = result(post("/v1/work-orders/{id}/reserve", workOrderId)
                .content(versionBody(workOrderId)), 201);
        result(post("/v1/work-orders/{id}/release", workOrderId).content(versionBody(workOrderId)), 200);
        JsonNode issue = result(post("/v1/material-issues")
                .header("Idempotency-Key", prefix + "ISSUE-" + fixture.suffix)
                .content(json(Map.of(
                        "workOrderId", workOrderId,
                        "reservationId", uuid(reservations.get(0), "reservationId"),
                        "quantity", new BigDecimal(reservations.get(0).get("quantity").asText())))), 201);
        UUID issueLineId = uuid(issue.get("lines").get(0), "issueLineId");
        UUID operationId = confirmFirstOperationInput(workOrderId, ORDERED_QUANTITY);
        result(post("/v1/work-orders/{id}/production-executions", workOrderId)
                .header("Idempotency-Key", prefix + "EXEC-" + fixture.suffix)
                .content(json(Map.of("goodQuantity", ORDERED_QUANTITY,
                        "actualStartedAt", EXEC_STARTED_AT,
                        "actualEndedAt", EXEC_ENDED_AT,
                        "expectedWorkOrderVersion", workOrderVersion(workOrderId),
                        "workOrderOperationId", operationId,
                        "consumption", List.of(Map.of("issueLineId", issueLineId,
                                "quantity", ORDERED_QUANTITY.multiply(QUANTITY_PER)))))), 201);
        result(post("/v1/work-orders/{id}/operations/{op}/finish", workOrderId, operationId)
                .content(versionBody(workOrderId)), 200);
        UUID receiptId = uuid(result(post("/v1/work-orders/{id}/production-receipts", workOrderId)
                .header("Idempotency-Key", prefix + "RECEIPT-" + fixture.suffix)
                .content(json(Map.of(
                        "destinationWarehouseId", fixture.warehouseId,
                        "quantity", ORDERED_QUANTITY,
                        "expectedWorkOrderVersion", workOrderVersion(workOrderId),
                        "receivedOn", plantToday().toString()))), 201), "receiptId");
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/submit",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200);
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/approve",
                workOrderId, receiptId).content(receiptVersions(workOrderId, receiptId)), 200);
        result(post("/v1/work-orders/{id}/production-receipts/{rid}/qc-disposition", workOrderId, receiptId)
                .content(json(Map.of("result", "AVAILABLE", "reason", "Passed E2E inspection"))), 200);

        String sourceVersion = result(get("/v1/work-orders/{id}/cost-checklist", workOrderId), 200)
                .get("sourceVersion").asText();
        long version = workOrderVersion(workOrderId);
        UUID runId = uuid(result(post("/v1/work-orders/{id}/cost-runs", workOrderId)
                .header("Idempotency-Key", prefix + "RUN-" + fixture.suffix)
                .content(json(Map.of("expectedVersion", version, "expectedSourceVersion", sourceVersion))), 200), "runId");
        assertThat(result(get("/v1/work-orders/{id}/production-costs", workOrderId), 200).get("readiness").asText())
                .isEqualTo("READY");
        return new Finalizable(workOrderId, runId, sourceVersion, version);
    }

    /**
     * A plain user (not an admin) who may finalize and close on one plant: the seeded MANAGER role (V17 variance read,
     * V90 close and calculate) through a PLANT scope on that plant.
     */
    private CloserGrant closerOnPlant(UUID plantId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder().username("closer-" + suffix).email("closer-" + suffix + "@e2e.local")
                .password("not-used").status(com.erp.manufacturing.module.user.domain.UserStatus.ACTIVE).build());
        UUID managerRoleId = roleRepository.findByCodeAndCompanyIdIsNull("MANAGER").orElseThrow().getRoleId();
        com.erp.manufacturing.module.organization.domain.AccessScope scope = accessScopeRepository.save(
                com.erp.manufacturing.module.organization.domain.AccessScope.builder()
                        .code("SC-CLOSE-" + suffix).name("Close scope " + suffix)
                        .scopeType(com.erp.manufacturing.module.organization.domain.ScopeType.PLANT)
                        .status(OrganizationStatus.ACTIVE).build());
        accessScopeResourceRepository.save(com.erp.manufacturing.module.organization.domain.AccessScopeResource.builder()
                .scopeId(scope.getScopeId())
                .resourceType(com.erp.manufacturing.module.organization.domain.ScopeResourceType.PLANT)
                .resourceId(plantId).build());
        UUID assignmentId = userRoleAssignmentRepository.save(
                com.erp.manufacturing.module.organization.domain.UserRoleAssignment.builder()
                        .userId(user.getUserId()).roleId(managerRoleId).scopeId(scope.getScopeId())
                        .status(com.erp.manufacturing.module.organization.domain.AssignmentStatus.ACTIVE).build())
                .getAssignmentId();
        UserPrincipal principal = new UserPrincipal(user, Set.of("MANAGER"), Set.of());
        return new CloserGrant(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))), assignmentId);
    }

    /** Bounded poll: some statement matching {@code pattern} is waiting on a row lock. */
    private void awaitBlockedOn(String pattern) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock' and query ilike ?
                    """, Integer.class, pattern);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no statement like " + pattern + " blocked on a lock within 30s");
    }

    private static void awaitLatch(java.util.concurrent.CountDownLatch latch) {
        try {
            if (!latch.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private record Finalizable(UUID workOrderId, UUID runId, String sourceVersion, long version) {}

    private record CloserGrant(Authentication authentication, UUID assignmentId) {}

    private Map<String, Object> finalizeBody(long workOrderVersion, String sourceVersion, UUID runId) {
        return Map.of("expectedVersion", workOrderVersion, "expectedSourceVersion", sourceVersion,
                "runId", runId, "expectedRunVersion", 1, "reason", "Cost reconciled by E2E");
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
                .content(json(Map.of("reason", "Cancelled by E2E scenario",
                        "expectedWorkOrderVersion", workOrderVersion(workOrderId)))), 200);

        mockMvc.perform(withDefaults(post("/v1/work-orders/{id}/reserve", workOrderId).content(versionBody(workOrderId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        assertThat(stockBalanceRepository
                .findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(fixture.componentId, fixture.warehouseId)
                .orElseThrow().getReservedQuantity())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Scrap allowance chain: BOM → MRP → MAKE conversion → WO → release baseline ───────────────
    // FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT_2026-10-30_v2 (R01–R06). Policy (PO 28/10, 30/10): requirement =
    // base × (1 + scrapRate); MRP rounds component gross UP to 6 decimals (B179), the work order HALF_UP to 6
    // (B273); the release baseline prices the work order's stored requiredQuantity at the standard cost of
    // RELEASE and never applies the allowance again. Every quantity below comes out of a real command.

    @Test
    @DisplayName("Scrap C01 (R01, R02, R04, R05): 2 KG × 10 PCS × 1.1 ⇒ MRP gross 22, WO 22, baseline 22000, not retroactive")
    void scrapAllowanceChain_c01_grossTwentyTwoFlowsFromBomToTheReleaseBaseline() throws Exception {
        ScrapChain chain = runScrapChain("C01", new BigDecimal("2"), new BigDecimal("0.1"), new BigDecimal("10"));

        // R05: the gross requirement is the scrap-inflated 22; net is 0 because 1000 KG are on hand.
        assertThat(decimal(chain.rmRequirement(), "grossRequiredQuantity")).isEqualByComparingTo("22");
        assertThat(decimal(chain.rmRequirement(), "netRequiredQuantity")).isEqualByComparingTo("0");
        assertThat(decimal(chain.rmRequirement(), "availableQuantity")).isEqualByComparingTo("1000");
        assertThat(decimal(chain.rmRequirement(), "safetyStockQuantity")).isEqualByComparingTo("0");
        assertThat(decimal(chain.componentLine(), "requiredQuantity")).isEqualByComparingTo("22");
        assertThat(decimal(chain.baselineMaterial(), "plannedQuantity")).isEqualByComparingTo("22");
        assertThat(decimal(chain.baselineMaterial(), "unitCost")).isEqualByComparingTo("1000");
        assertThat(decimal(chain.baselineMaterial(), "plannedAmount")).isEqualByComparingTo("22000");
        assertThat(decimal(chain.baseline().get("planned"), "material")).isEqualByComparingTo("22000");

        // R04: a standard cost saved after release reaches neither the baseline nor the run nor the work order.
        result(put("/v1/companies/{c}/items/{i}/standard-cost", fixture.companyId, chain.rmItemId())
                .content(json(Map.of("materialCost", 2500, "laborCost", 0, "overheadCost", 0))), 200);
        JsonNode baselineAfter = result(get("/v1/work-orders/{id}/cost-baseline", chain.workOrderId()), 200)
                .get("baseline");
        assertThat(baselineAfter.get("baselineId").asText()).isEqualTo(chain.baseline().get("baselineId").asText());
        JsonNode materialAfter = baselineAfter.get("materials").get(0);
        assertThat(decimal(materialAfter, "plannedQuantity")).isEqualByComparingTo("22");
        assertThat(decimal(materialAfter, "unitCost")).isEqualByComparingTo("1000");
        assertThat(decimal(materialAfter, "plannedAmount")).isEqualByComparingTo("22000");
        assertThat(decimal(baselineAfter.get("planned"), "material")).isEqualByComparingTo("22000");

        // Read back after reload: what was stored, not what the command echoed.
        JsonNode workOrderAgain = result(get("/v1/work-orders/{id}", chain.workOrderId()), 200);
        assertThat(decimal(workOrderAgain.get("componentLines").get(0), "requiredQuantity")).isEqualByComparingTo("22");
        assertThat(decimal(workOrderAgain, "plannedQuantity")).isEqualByComparingTo("10");
        JsonNode rmAgain = requirementOf(result(get("/v1/planning-runs/{id}/requirements", chain.runId()), 200)
                .get("content"), chain.rmItemId());
        assertThat(decimal(rmAgain, "grossRequiredQuantity")).isEqualByComparingTo("22");
        assertThat(decimal(rmAgain, "netRequiredQuantity")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Scrap C02 (R03, R04): 10 × 1.000001 × 1.000001 ⇒ MRP CEILING 10.000021, WO HALF_UP 10.000020, baseline from WO")
    void scrapAllowanceChain_c02_mrpRoundsUpTheWorkOrderHalfUpAndTheBaselineFollowsTheWorkOrder() throws Exception {
        // Unrounded: 10 × 1.000001 × 1.000001 = 10.00002000001
        ScrapChain chain = runScrapChain("C02", new BigDecimal("1.000001"), new BigDecimal("0.000001"),
                new BigDecimal("10"));

        BigDecimal mrp = decimal(chain.rmRequirement(), "grossRequiredQuantity");
        BigDecimal workOrder = decimal(chain.componentLine(), "requiredQuantity");
        assertThat(mrp).isEqualByComparingTo("10.000021");
        assertThat(workOrder).isEqualByComparingTo("10.000020");
        assertThat(mrp.subtract(workOrder).abs()).isLessThanOrEqualTo(new BigDecimal("0.000001"));
        assertThat(decimal(chain.baselineMaterial(), "plannedQuantity")).isEqualByComparingTo("10.000020");
        assertThat(decimal(chain.baselineMaterial(), "plannedAmount")).isEqualByComparingTo("10000.020000");
        assertThat(decimal(chain.baseline().get("planned"), "material")).isEqualByComparingTo("10000.020000");
    }

    @Test
    @DisplayName("Scrap C03 (R03): 1.2 × 1.000001 × 1.1 ⇒ MRP 1.320002, WO 1.320001, baseline 1320.001000")
    void scrapAllowanceChain_c03_fractionalPlannedQuantityKeepsTheSameSourceRounding() throws Exception {
        // Unrounded: 1.2 × 1.000001 × 1.1 = 1.32000132
        ScrapChain chain = runScrapChain("C03", new BigDecimal("1.000001"), new BigDecimal("0.1"),
                new BigDecimal("1.2"));

        BigDecimal mrp = decimal(chain.rmRequirement(), "grossRequiredQuantity");
        BigDecimal workOrder = decimal(chain.componentLine(), "requiredQuantity");
        assertThat(mrp).isEqualByComparingTo("1.320002");
        assertThat(workOrder).isEqualByComparingTo("1.320001");
        assertThat(mrp.subtract(workOrder).abs()).isLessThanOrEqualTo(new BigDecimal("0.000001"));
        assertThat(decimal(chain.baselineMaterial(), "plannedQuantity")).isEqualByComparingTo("1.320001");
        assertThat(decimal(chain.baselineMaterial(), "plannedAmount")).isEqualByComparingTo("1320.001000");
    }

    /**
     * One scrap-allowance chain on its own finished good (PCS, not lot-tracked, no stock, safety 0) and raw material
     * (KG, 1000 on hand, standard material cost 1000): sales order → confirm → planning run → approve + convert the
     * MAKE proposal → reserve → release → cost baseline. Asserts the planned quantities and that every step points
     * at the same source (run, proposal, requirement line, BOM, BOM line, component line), so any difference left
     * between MRP and the work order is the same-source rounding the policy allows.
     */
    private ScrapChain runScrapChain(String tag, BigDecimal quantityPer, BigDecimal scrapRate, BigDecimal ordered)
            throws Exception {
        Company company = companyRepository.findById(fixture.companyId).orElseThrow();
        Plant plant = plantRepository.findById(fixture.plantId).orElseThrow();
        Warehouse warehouse = warehouseRepository.findById(fixture.warehouseId).orElseThrow();

        Item finishedGood = itemRepository.save(Item.builder()
                .company(company).code("FGS-" + tag + "-" + fixture.suffix).name("E2E Scrap Finished Good")
                .type(ItemType.FINISHED_GOOD).unit("PCS").lotTracked(false)
                .status(ItemStatus.ACTIVE).build());
        Item rawMaterial = itemRepository.save(Item.builder()
                .company(company).code("RMS-" + tag + "-" + fixture.suffix).name("E2E Scrap Raw Material")
                .type(ItemType.RAW_MATERIAL).unit("KG").lotTracked(false)
                .status(ItemStatus.ACTIVE).build());

        BomHeader bom = BomHeader.builder()
                .company(company).parentItem(finishedGood).revision("A")
                .bomCode("BOMS-" + UUID.randomUUID()).versionNumber(1)
                .status(BomStatus.ACTIVE).description("E2E scrap BOM").build();
        BomLine bomLine = BomLine.builder()
                .bom(bom).componentItem(rawMaterial).lineNo(1)
                .quantityPer(quantityPer).scrapRate(scrapRate).build();
        bom.getLines().add(bomLine);
        bom = bomHeaderRepository.save(bom);
        UUID bomLineId = bom.getLines().get(0).getLineId();

        WorkCenter workCenter = workCenterRepository.save(WorkCenter.builder()
                .plant(plant).code("WCS-" + tag + "-" + fixture.suffix).name("E2E Scrap Work Center")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build());
        RoutingHeader routing = RoutingHeader.builder()
                .company(company).item(finishedGood).code("RTS-" + tag + "-" + fixture.suffix)
                .routingVersion("1").versionNumber(1).status(RoutingStatus.ACTIVE).build();
        routing.getOperations().add(RoutingOperation.builder()
                .routing(routing).sequence(10).name("Assemble").workCenter(workCenter)
                .setupMinutes(new BigDecimal("5")).runMinutesPerUnit(new BigDecimal("1")).build());
        routingHeaderRepository.save(routing);

        // No lot sizing exists for MAKE; safety stock and reorder point 0 and no finished stock ⇒ planned = ordered.
        itemWarehouseSettingRepository.save(ItemWarehouseSetting.builder()
                .item(finishedGood).warehouse(warehouse).safetyStock(BigDecimal.ZERO)
                .reorderPoint(BigDecimal.ZERO).leadTimeDays(0)
                .status(ItemWarehouseSettingStatus.ACTIVE).build());
        stockBalanceRepository.save(StockBalance.builder()
                .item(rawMaterial).warehouse(warehouse).quantity(new BigDecimal("1000"))
                .reservedQuantity(BigDecimal.ZERO).build());
        result(put("/v1/companies/{c}/items/{i}/standard-cost", fixture.companyId, rawMaterial.getItemId())
                .content(json(Map.of("materialCost", 1000, "laborCost", 0, "overheadCost", 0))), 200);

        JsonNode order = result(post("/sales-orders/v1")
                .content(json(Map.of(
                        "companyId", fixture.companyId,
                        "plantId", fixture.plantId,
                        "orderNo", "SO-SCRAP-" + tag + "-" + fixture.suffix,
                        "customerName", "E2E Customer",
                        "customerId", fixture.customerId,
                        "orderDate", ORDER_DATE.toString(),
                        "lines", List.of(Map.of(
                                "itemId", finishedGood.getItemId(),
                                "orderedQuantity", ordered,
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
        UUID runId = uuid(run, "mrpRunId");
        JsonNode requirements = result(get("/v1/planning-runs/{id}/requirements", runId), 200).get("content");
        JsonNode fgRequirement = requirementOf(requirements, finishedGood.getItemId());
        JsonNode rmRequirement = requirementOf(requirements, rawMaterial.getItemId());
        assertThat(fgRequirement.get("requirementLevel").asInt()).isZero();
        assertThat(decimal(fgRequirement, "grossRequiredQuantity")).isEqualByComparingTo(ordered);
        assertThat(decimal(fgRequirement, "netRequiredQuantity")).isEqualByComparingTo(ordered);
        assertThat(rmRequirement.get("requirementLevel").asInt()).isEqualTo(1);
        assertThat(uuid(rmRequirement, "parentRequirementLineId"))
                .isEqualTo(uuid(fgRequirement, "mrpRequirementLineId"));
        assertThat(uuid(rmRequirement, "sourceDemandId")).isEqualTo(demand.getPlanningDemandId());

        JsonNode suggestions = result(get("/v1/planning-runs/{id}/suggestions", runId), 200).get("content");
        assertThat(suggestions.size()).as("suggestions were %s", suggestions).isEqualTo(1);
        JsonNode suggestion = suggestions.get(0);
        assertThat(suggestion.get("supplyType").asText()).isEqualTo("MAKE");
        assertThat(uuid(suggestion, "requirementLineId")).isEqualTo(uuid(fgRequirement, "mrpRequirementLineId"));
        assertThat(decimal(suggestion, "suggestedQuantity")).isEqualByComparingTo(ordered);
        UUID suggestionId = uuid(suggestion, "supplySuggestionId");

        result(post("/v1/supply-suggestions/{id}/approve", suggestionId)
                .content(json(Map.of("decisionNote", "approved by E2E"))), 200);
        UUID workOrderId = uuid(result(post("/v1/supply-suggestions/{id}/convert-to-work-order", suggestionId)
                .content(json(Map.of("outputWarehouseId", fixture.warehouseId))), 200), "convertedWorkOrderId");

        JsonNode workOrder = result(get("/v1/work-orders/{id}", workOrderId), 200);
        assertThat(uuid(workOrder, "planningRunId")).isEqualTo(runId);
        assertThat(uuid(workOrder, "planningProposalId")).isEqualTo(suggestionId);
        assertThat(uuid(workOrder, "bomId")).isEqualTo(bom.getBomId());
        assertThat(decimal(workOrder, "plannedQuantity")).isEqualByComparingTo(ordered);
        assertThat(workOrder.get("componentLines").size()).isEqualTo(1);
        JsonNode componentLine = workOrder.get("componentLines").get(0);
        assertThat(uuid(componentLine, "bomLineId")).isEqualTo(bomLineId);
        assertThat(uuid(componentLine, "componentItemId")).isEqualTo(rawMaterial.getItemId());
        assertThat(decimal(componentLine, "quantityPer")).isEqualByComparingTo(quantityPer);
        assertThat(decimal(componentLine, "scrapRate")).isEqualByComparingTo(scrapRate);

        result(post("/v1/work-orders/{id}/reserve", workOrderId).content(versionBody(workOrderId)), 201);
        result(post("/v1/work-orders/{id}/release", workOrderId).content(versionBody(workOrderId)), 200);
        assertThat(result(get("/v1/work-orders/{id}", workOrderId), 200).get("status").asText()).isEqualTo("RELEASED");

        JsonNode baseline = result(get("/v1/work-orders/{id}/cost-baseline", workOrderId), 200).get("baseline");
        assertThat(baseline).as("release captures a baseline").isNotNull();
        assertThat(decimal(baseline, "plannedQuantity")).isEqualByComparingTo(ordered);
        assertThat(baseline.get("materials").size()).isEqualTo(1);
        JsonNode baselineMaterial = baseline.get("materials").get(0);
        assertThat(uuid(baselineMaterial, "componentLineId")).isEqualTo(uuid(componentLine, "componentLineId"));
        assertThat(uuid(baselineMaterial, "itemId")).isEqualTo(rawMaterial.getItemId());
        assertThat(baselineMaterial.get("uom").asText()).isEqualTo("KG");
        assertThat(rmRequirement.get("uom").asText()).isEqualTo("KG");
        assertThat(componentLine.get("uom").asText()).isEqualTo("KG");

        return new ScrapChain(rawMaterial.getItemId(), runId, workOrderId, rmRequirement, componentLine,
                baseline, baselineMaterial);
    }

    private record ScrapChain(UUID rmItemId, UUID runId, UUID workOrderId, JsonNode rmRequirement,
                              JsonNode componentLine, JsonNode baseline, JsonNode baselineMaterial) {}

    private JsonNode requirementOf(JsonNode requirements, UUID itemId) {
        List<JsonNode> matches = new java.util.ArrayList<>();
        requirements.forEach(line -> {
            if (itemId.toString().equals(line.get("itemId").asText())) {
                matches.add(line);
            }
        });
        assertThat(matches).as("requirement lines of %s in %s", itemId, requirements).hasSize(1);
        return matches.get(0);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        assertThat(node.get(field)).as("field %s of %s", field, node).isNotNull();
        assertThat(node.get(field).isNull()).as("field %s of %s is null", field, node).isFalse();
        return new BigDecimal(node.get(field).asText());
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
                .bomCode("BOM-" + java.util.UUID.randomUUID()).versionNumber(1)
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
                .company(company).item(finishedGood).code("RT-" + suffix).routingVersion("1").versionNumber(1)
                .status(RoutingStatus.ACTIVE).build();
        routing.getOperations().add(RoutingOperation.builder()
                .routing(routing).sequence(10).name("Assemble").workCenter(workCenter)
                .setupMinutes(new BigDecimal("5")).runMinutesPerUnit(new BigDecimal("1")).build());
        routingHeaderRepository.save(routing);

        stockBalanceRepository.save(StockBalance.builder()
                .item(component).warehouse(warehouse).quantity(COMPONENT_ON_HAND)
                .reservedQuantity(BigDecimal.ZERO).build());

        // G10a (D4): confirming a sales order needs a master customer.
        com.erp.manufacturing.module.sales.domain.Customer customer = customerRepository.save(
                com.erp.manufacturing.module.sales.domain.Customer.builder()
                        .company(company).code("CUS-" + suffix).name("E2E Customer")
                        .status(com.erp.manufacturing.module.sales.domain.CustomerStatus.ACTIVE).build());

        return new Fixture(suffix, company.getCompanyId(), plant.getPlantId(),
                warehouse.getWarehouseId(), finishedGood.getItemId(), component.getItemId(), customer.getCustomerId());
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
                .bomCode("BOM-" + java.util.UUID.randomUUID()).versionNumber(1)
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
                .company(company).item(finishedGood).code("RTN-" + fixture.suffix).routingVersion("1").versionNumber(1)
                .status(RoutingStatus.ACTIVE).build();
        routing.getOperations().add(RoutingOperation.builder()
                .routing(routing).sequence(10).name("Assemble").workCenter(workCenter)
                .setupMinutes(new BigDecimal("5")).runMinutesPerUnit(new BigDecimal("1")).build());
        routingHeaderRepository.save(routing);

        itemWarehouseSettingRepository.save(ItemWarehouseSetting.builder()
                .item(finishedGood).warehouse(warehouse).safetyStock(BigDecimal.ZERO)
                .reorderPoint(BigDecimal.ZERO).leadTimeDays(0)
                .status(ItemWarehouseSettingStatus.ACTIVE).build());

        return new NonLotFixture(finishedGood.getItemId(), workCenter.getWorkCenterId());
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
    /**
     * G8 (D5): every work order command carries the version last read. Read fresh each time — a refused
     * release persists BLOCKED in its own transaction and moves the version on behind the caller's back.
     */
    /** G9a: the receipt's and the work order's versions, read back from the receipt detail. */
    private String receiptVersions(UUID workOrderId, UUID receiptId) throws Exception {
        JsonNode receipt = result(get("/v1/work-orders/{id}/production-receipts/{rid}", workOrderId, receiptId), 200);
        return json(Map.of("expectedVersion", receipt.get("version").asLong(),
                "expectedWorkOrderVersion", receipt.get("workOrderVersion").asLong()));
    }

    /** The receipt's business date: the fixture plant keeps the default time zone. */
    private static java.time.LocalDate plantToday() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private long workOrderVersion(UUID workOrderId) throws Exception {
        return result(get("/v1/work-orders/{id}", workOrderId), 200).get("version").asLong();
    }

    private String versionBody(UUID workOrderId) throws Exception {
        return json(Map.of("expectedWorkOrderVersion", workOrderVersion(workOrderId)));
    }

    /** G8c: confirms input of the work order's first operation and returns its id. */
    private UUID confirmFirstOperationInput(UUID workOrderId, BigDecimal quantity) throws Exception {
        UUID operationId = uuid(result(get("/v1/work-orders/{id}/operations", workOrderId), 200).get(0),
                "workOrderOperationId");
        JsonNode input = result(post("/v1/work-orders/{id}/operations/{op}/input", workOrderId, operationId)
                .content(json(Map.of("expectedWorkOrderVersion", workOrderVersion(workOrderId), "quantity", quantity))), 200);
        assertThat(new BigDecimal(input.get("inputQuantity").asText())).isEqualByComparingTo(quantity);
        return operationId;
    }

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

    private record NonLotFixture(UUID itemId, UUID workCenterId) {}

    private record Fixture(String suffix,
                           UUID companyId,
                           UUID plantId,
                           UUID warehouseId,
                           UUID finishedGoodId,
                           UUID componentId,
                           UUID customerId) {}
}
