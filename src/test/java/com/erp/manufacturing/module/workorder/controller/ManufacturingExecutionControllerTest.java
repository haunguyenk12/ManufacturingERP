package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import org.springframework.context.annotation.Import;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionCandidateResponse;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionPostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionExecutionResponse;
import com.erp.manufacturing.module.workorder.dto.execution.ProductionReceiptCandidateResponse;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionExecutionService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionReceiptService;
import com.erp.manufacturing.module.workorder.service.execution.WipTransactionService;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderVarianceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link ManufacturingExecutionController} (`D7`) – locks the
 * {@code {code,result,message}} envelope (`.claude/rules/error-handling.md` §5.1) and the
 * Idempotency-Key forwarding (rule `R9`) on the endpoint that actually advances a work order
 * (bất biến B53).
 *
 * <p>🔴 {@code X-Plant-Id} applies per <b>endpoint</b>, not per controller (§5.6.1), and this class
 * pins both sides of that boundary: the plant-scoped endpoints (the two candidate screens, the two
 * flat lists) return <b>409</b> on a mismatched header, while
 * {@code GET /production-executions?workOrderId=} returns <b>200</b> and ignores it — it is
 * identified by its aggregate, so there is no second plant value to disagree with. Read
 * {@link PlantContextResolver} before "wiring it for consistency".
 *
 * <p>Permission checks (`@PreAuthorize`) live in the service layer (C1) and are covered by the
 * {@code *MethodSecurityTest} classes – not re-tested here.
 */
@WebMvcTest(controllers = ManufacturingExecutionController.class)
@AutoConfigureMockMvc(addFilters = false)
// Real bean, not a @MockBean: it carries the X-Plant-Id cross-check the candidates endpoint relies
// on, and mocking it into a no-op would delete the thing under test (R3). Same as WorkOrderControllerTest.
@Import(PlantContextResolver.class)
@DisplayName("ManufacturingExecutionController – response envelope contract")
class ManufacturingExecutionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MaterialReservationService reservationService;
    @MockBean
    MaterialIssueService materialIssueService;
    @MockBean
    WipTransactionService wipTransactionService;
    @MockBean
    ProductionExecutionService productionExecutionService;
    @MockBean
    ProductionReceiptService productionReceiptService;
    @MockBean
    WorkOrderVarianceService varianceService;

    // Unused directly by these tests – required only so the auto-detected security filters
    // can be constructed by the @WebMvcTest slice, even with addFilters = false.
    @MockBean
    IpExtractor ipExtractor;
    @MockBean
    JwtTokenProvider jwtTokenProvider;
    @MockBean
    TokenStoreService tokenStoreService;
    @MockBean
    UserDetailsService userDetailsService;
    @MockBean
    RedisTemplate<String, String> redisTemplate;
    @MockBean
    RateLimitProperties rateLimitProperties;

    private static final UUID WORK_ORDER_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID  = UUID.randomUUID();
    private static final UUID PLANT_ID      = UUID.randomUUID();

    /** 10 planned, 6 reported good ⇒ 4 still reportable (B75). */
    private ProductionExecutionCandidateResponse sampleCandidate() {
        return new ProductionExecutionCandidateResponse(
                WORK_ORDER_ID, "WO-001", "IN_PROGRESS",
                UUID.randomUUID(), "FG-100", "Widget", "EA",
                new BigDecimal("10.000000"), new BigDecimal("6.000000"), new BigDecimal("4.000000"),
                Instant.now());
    }

    private ProductionExecutionResponse sampleExecution() {
        return new ProductionExecutionResponse(
                EXECUTION_ID, "PE-0A1B2C3D", "POSTED",
                WORK_ORDER_ID, "WO-001", null, null, null, null,
                new BigDecimal("6.000000"), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, "operator1", "trace-1", null,
                "PCS",
                new BigDecimal("6.000000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("6.000000"),
                new BigDecimal("4.000000"), new BigDecimal("60.00"),
                "IN_PROGRESS", Instant.now());
    }

    /** Both timestamps are mandatory since F7 (spec §5.1) — a body without them is a 400. */
    private static final String REPORT_BODY = """
            {"goodQuantity":6,"scrapQuantity":0,"reworkQuantity":0,
             "actualStartedAt":"2026-08-03T08:00:00Z","actualEndedAt":"2026-08-03T16:00:00Z"}
            """;

    @Test
    @DisplayName("production-executions: Idempotency-Key reaches the service unchanged and returns 201")
    void reportProduction_withIdempotencyKeyHeader_forwardsHeaderAndReturns201() throws Exception {
        when(productionExecutionService.report(
                eq(WORK_ORDER_ID), any(ProductionExecutionPostRequest.class), eq("EXEC-KEY")))
                .thenReturn(sampleExecution());

        mockMvc.perform(post("/api/v1/work-orders/" + WORK_ORDER_ID + "/production-executions")
                        .header("Idempotency-Key", "EXEC-KEY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.productionExecutionId").value(EXECUTION_ID.toString()))
                .andExpect(jsonPath("$.result.goodQuantity").value(6))
                // B53: the report – not the receipt – is what advances the work order
                .andExpect(jsonPath("$.result.workOrderStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.workOrderAvailableToReceipt").value(6));

        verify(productionExecutionService).report(
                eq(WORK_ORDER_ID), any(ProductionExecutionPostRequest.class), eq("EXEC-KEY"));
    }

    @Test
    @DisplayName("production-executions: work order not in an executable status returns 409 STATE_CONFLICT (B13)")
    void reportProduction_workOrderNotExecutable_returns409StateConflict() throws Exception {
        when(productionExecutionService.report(
                eq(WORK_ORDER_ID), any(ProductionExecutionPostRequest.class), isNull()))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Work order status does not allow execution"));

        mockMvc.perform(post("/api/v1/work-orders/" + WORK_ORDER_ID + "/production-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT_BODY))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("production-executions: cumulative good over plan returns 409 PLANNED_QUANTITY_EXCEEDED (B54)")
    void reportProduction_overPlannedQuantity_returns409PlannedQuantityExceeded() throws Exception {
        when(productionExecutionService.report(
                eq(WORK_ORDER_ID), any(ProductionExecutionPostRequest.class), isNull()))
                .thenThrow(new AppException(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                        "Cumulative good quantity would exceed the planned quantity"));

        mockMvc.perform(post("/api/v1/work-orders/" + WORK_ORDER_ID + "/production-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT_BODY))
                .andExpect(status().is(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED.code()));
    }

    /**
     * Spec §5.1 marks both timestamps mandatory (F7). They are {@code @NotNull} on the request record,
     * so the refusal happens at the {@code @Valid} boundary — which makes this the only layer that can
     * observe it.
     */
    @Test
    @DisplayName("production-executions: a report without the shift window returns 400 VALIDATION_ERROR")
    void reportProduction_withoutActualTimestamps_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/" + WORK_ORDER_ID + "/production-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goodQuantity":6,"scrapQuantity":0,"reworkQuantity":0}
                                """))
                .andExpect(status().is(ValidationErrorCode.INVALID_INPUT.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("actualStartedAt", "actualEndedAt")));

        verifyNoInteractions(productionExecutionService);
    }

    @Test
    @DisplayName("candidates: PageResult envelope is part of the contract (spec §5.1)")
    void listExecutionCandidates_returns200WithFullPageEnvelope() throws Exception {
        when(productionExecutionService.listCandidates(eq(PLANT_ID), any()))
                .thenReturn(new PageResult<>(List.of(sampleCandidate()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/production-executions/candidates").param("plantId", PLANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].workOrderId").value(WORK_ORDER_ID.toString()))
                // Spec §6.4 vocabulary: the SKU field is itemSku, not itemCode.
                .andExpect(jsonPath("$.result.content[0].itemSku").value("FG-100"))
                // §5.2 "Context" is a four-cell panel; F8 filled three and left uom out (F9).
                .andExpect(jsonPath("$.result.content[0].uom").value("EA"))
                .andExpect(jsonPath("$.result.content[0].remainingQuantity").value(4))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    /**
     * The candidates endpoint names a plant explicitly, so it is the one endpoint on this controller
     * that has a second source of plant to cross-check the header against (§5.6.1). A mismatch must be
     * refused before the service is reached, not after.
     */
    @Test
    @DisplayName("candidates: X-Plant-Id disagreeing with plantId returns 409 STATE_CONFLICT")
    void listExecutionCandidates_plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(get("/api/v1/production-executions/candidates")
                        .param("plantId", PLANT_ID.toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(productionExecutionService);
    }

    // ---------------------------------------------------------------------------------------
    // F8 — the four flat/candidate endpoints spec §4.1, §5.1 and §6.2 ask for
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("receipt candidates: PageResult envelope is part of the contract (spec §6.2)")
    void listReceiptCandidates_returns200WithFullPageEnvelope() throws Exception {
        when(productionReceiptService.listCandidates(eq(PLANT_ID), any()))
                .thenReturn(new PageResult<>(List.of(sampleReceiptCandidate()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/production-receipts/candidates").param("plantId", PLANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].workOrderId").value(WORK_ORDER_ID.toString()))
                .andExpect(jsonPath("$.result.content[0].outputItemSku").value("FG-100"))
                .andExpect(jsonPath("$.result.content[0].uom").value("EA"))
                // Net of open receipts (B16/B76), not actualGood - receipted.
                .andExpect(jsonPath("$.result.content[0].availableToReceipt").value(4))
                .andExpect(jsonPath("$.result.content[0].outputTrackingMethod").value("NON_TRACKED"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("receipt candidates: X-Plant-Id disagreeing with plantId returns 409 STATE_CONFLICT")
    void listReceiptCandidates_plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(get("/api/v1/production-receipts/candidates")
                        .param("plantId", PLANT_ID.toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(productionReceiptService);
    }

    @Test
    @DisplayName("flat receipts: plant-scoped list forwards the optional status filter (spec §6.2)")
    void listProductionReceiptsByPlant_forwardsTheStatusFilter() throws Exception {
        when(productionReceiptService.listByPlant(eq(PLANT_ID), eq(ProductionReceiptStatus.DRAFT), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(get("/api/v1/production-receipts")
                        .param("plantId", PLANT_ID.toString())
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.totalElements").value(0));

        verify(productionReceiptService).listByPlant(eq(PLANT_ID), eq(ProductionReceiptStatus.DRAFT), any());
    }

    /** Omitting {@code status} must mean "every status", not "the first one" (§6.2). */
    @Test
    @DisplayName("flat receipts: omitting status passes null rather than defaulting to a status")
    void listProductionReceiptsByPlant_withoutStatus_passesNull() throws Exception {
        when(productionReceiptService.listByPlant(eq(PLANT_ID), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(get("/api/v1/production-receipts").param("plantId", PLANT_ID.toString()))
                .andExpect(status().isOk());

        verify(productionReceiptService).listByPlant(eq(PLANT_ID), isNull(), any());
    }

    @Test
    @DisplayName("flat receipts: X-Plant-Id disagreeing with plantId returns 409 STATE_CONFLICT")
    void listProductionReceiptsByPlant_plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(get("/api/v1/production-receipts")
                        .param("plantId", PLANT_ID.toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(productionReceiptService);
    }

    @Test
    @DisplayName("flat material issues: plant-scoped list forwards the optional workOrderId (spec §4.1)")
    void listMaterialIssuesByPlant_forwardsTheWorkOrderFilter() throws Exception {
        when(materialIssueService.listByPlant(eq(PLANT_ID), eq(WORK_ORDER_ID), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(get("/api/v1/material-issues")
                        .param("plantId", PLANT_ID.toString())
                        .param("workOrderId", WORK_ORDER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(materialIssueService).listByPlant(eq(PLANT_ID), eq(WORK_ORDER_ID), any());
    }

    @Test
    @DisplayName("flat material issues: X-Plant-Id disagreeing with plantId returns 409 STATE_CONFLICT")
    void listMaterialIssuesByPlant_plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(get("/api/v1/material-issues")
                        .param("plantId", PLANT_ID.toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(materialIssueService);
    }

    @Test
    @DisplayName("flat production executions: delegates to the same service call as the nested list")
    void listProductionExecutionsFlat_returns200() throws Exception {
        when(productionExecutionService.list(eq(WORK_ORDER_ID), any()))
                .thenReturn(new PageResult<>(List.of(sampleExecution()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/production-executions")
                        .param("workOrderId", WORK_ORDER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].workOrderCode").value("WO-001"))
                .andExpect(jsonPath("$.result.content[0].operatorUsername").value("operator1"))
                .andExpect(jsonPath("$.result.content[0].uom").value("PCS"))
                .andExpect(jsonPath("$.result.content[0].workOrderCompletionPercent").value(60.00));
    }

    /**
     * 🔴 Pins the §5.6.1 boundary, which is per <em>endpoint</em> and not per controller. This one is
     * identified by its aggregate, so a disagreeing {@code X-Plant-Id} has nothing to disagree with
     * and must be ignored — 200, not 409. A comment cannot fail; this can.
     *
     * <p>Contrast the tests above, where the same header on a plant-scoped endpoint is a 409.
     */
    @Test
    @DisplayName("flat production executions: a mismatched X-Plant-Id is ignored, not a 409 (§5.6.1)")
    void listProductionExecutionsFlat_ignoresThePlantHeader() throws Exception {
        when(productionExecutionService.list(eq(WORK_ORDER_ID), any()))
                .thenReturn(new PageResult<>(List.of(sampleExecution()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/production-executions")
                        .param("workOrderId", WORK_ORDER_ID.toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(productionExecutionService).list(eq(WORK_ORDER_ID), any());
    }

    /** 10 produced good, none warehoused, 6 already claimed by an open receipt ⇒ 4 receiptable. */
    private ProductionReceiptCandidateResponse sampleReceiptCandidate() {
        return new ProductionReceiptCandidateResponse(
                WORK_ORDER_ID, "WO-001", "COMPLETED",
                UUID.randomUUID(), "FG-100", "Widget", "EA",
                new BigDecimal("10.000000"), BigDecimal.ZERO, new BigDecimal("4.000000"),
                "NON_TRACKED", Instant.now());
    }

    @Test
    @DisplayName("production-executions list: PageResult envelope is part of the contract")
    void listProductionExecutions_returns200WithFullPageEnvelope() throws Exception {
        when(productionExecutionService.list(eq(WORK_ORDER_ID), any()))
                .thenReturn(new PageResult<>(List.of(sampleExecution()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/work-orders/" + WORK_ORDER_ID + "/production-executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].productionExecutionId").value(EXECUTION_ID.toString()))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }
}
