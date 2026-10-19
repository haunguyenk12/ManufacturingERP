package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.web.PlantContextResolver;
import org.springframework.context.annotation.Import;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderComponentLineResponse;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderResponse;
import com.erp.manufacturing.module.workorder.dto.execution.WorkOrderComponentIssueRequest;
import com.erp.manufacturing.module.workorder.service.WorkOrderService;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderPlanRequest;
import com.erp.manufacturing.module.workorder.dto.schedule.WorkOrderSchedulePreviewResponse;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderReadinessService;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderSchedulePreviewService;
import org.mockito.ArgumentCaptor;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link WorkOrderController} – locks the {@code {code,result,message}} envelope
 * and the Idempotency-Key header forwarding pattern (`.claude/rules/error-handling.md` §5.1,
 * `NEXT_PHASE_PLAN.md` T2 §4.3). Permission checks (`@PreAuthorize`) live in the service layer
 * (C1) and are already covered by {@code WorkOrderMethodSecurityTest} – not re-tested here.
 */
@WebMvcTest(controllers = WorkOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
// R3: PlantContextResolver holds real X-Plant-Id validation logic, so import the real bean
// rather than mocking it into a no-op.
@Import(PlantContextResolver.class)
@DisplayName("WorkOrderController – response envelope contract")
class WorkOrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    WorkOrderService workOrderService;
    @MockBean
    WorkOrderReadinessService readinessService;
    @MockBean
    WorkOrderSchedulePreviewService schedulePreviewService;

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

    private static final UUID PLANT_ID       = UUID.randomUUID();
    private static final UUID WORK_ORDER_ID  = UUID.randomUUID();

    private WorkOrderResponse sampleResponse() {
        return new WorkOrderResponse(
                WORK_ORDER_ID, UUID.randomUUID(), PLANT_ID, "PLANT-01", "WO-001",
                UUID.randomUUID(), "ITEM-001", "Widget", "PCS",
                UUID.randomUUID(), "REV-1",
                null, // bomCapturedAt (F8)
                null, null, null, null,
                null, null, null, // planningRunId / Code / ProposalId (F8)
                UUID.randomUUID(), "WH-01",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                // plannedStartAt, plannedEndAt, releasedAt, executionStartedAt (F8),
                // executionCompletedAt (F8), completedAt, cancelledAt, cancelReason, blockedAt,
                // blockReason, notes, createdAt, updatedAt, componentLines, operations, allocations
                "DRAFT", null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    @Test
    @DisplayName("create: valid request returns 201 Created")
    void createWorkOrder_validRequest_returns201Created() throws Exception {
        when(workOrderService.create(eq(PLANT_ID), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/plants/" + PLANT_ID + "/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workOrderNo":"WO-001","productItemId":"%s","outputWarehouseId":"%s","plannedQuantity":10}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.workOrderId").value(WORK_ORDER_ID.toString()));
    }

    @Test
    @DisplayName("create: missing required field fails @Valid before reaching the service")
    void createWorkOrder_missingRequiredField_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/v1/plants/" + PLANT_ID + "/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workOrderNo":"WO-001"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
    }

    @Test
    @DisplayName("release: insufficient reservation (bất biến B14) returns 409 STATE_CONFLICT")
    void releaseWorkOrder_insufficientReservation_returns409() throws Exception {
        when(workOrderService.release(eq(WORK_ORDER_ID), any(), any()))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Reservation does not fully cover component requirements"));

        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedWorkOrderVersion\":3}"))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("close: completed work order returns 200 with the response envelope")
    void closeWorkOrder_completed_returns200() throws Exception {
        when(workOrderService.close(WORK_ORDER_ID)).thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.workOrderId").value(WORK_ORDER_ID.toString()));
    }

    @Test
    @DisplayName("close: not-completed work order returns 409 STATE_CONFLICT")
    void closeWorkOrder_notCompleted_returns409() throws Exception {
        when(workOrderService.close(WORK_ORDER_ID))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only completed work orders can be closed"));

        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/close"))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    /**
     * Spec §3.3 "Requirement" row: the component table needs {@code uom} (F8) and
     * {@code reservedQuantity} (F9) on the work order itself. Before F9 the frontend had to call
     * {@code /material-readiness} as well to fill one table.
     */
    @Test
    @DisplayName("get: component lines carry uom and reservedQuantity (spec §3.3)")
    void getWorkOrder_componentLineCarriesUomAndReservedQuantity() throws Exception {
        when(workOrderService.get(WORK_ORDER_ID)).thenReturn(responseWithComponentLine());

        mockMvc.perform(get("/v1/work-orders/" + WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.componentLines[0].uom").value("KG"))
                .andExpect(jsonPath("$.result.componentLines[0].requiredQuantity").value(10))
                .andExpect(jsonPath("$.result.componentLines[0].reservedQuantity").value(6))
                .andExpect(jsonPath("$.result.componentLines[0].issuedQuantity").value(2));
    }

    private WorkOrderResponse responseWithComponentLine() {
        WorkOrderResponse base = sampleResponse();
        WorkOrderComponentLineResponse line = new WorkOrderComponentLineResponse(
                UUID.randomUUID(), UUID.randomUUID(), 10,
                UUID.randomUUID(), "RM-001", "Raw material", "KG",
                BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.TEN, new BigDecimal("6"), new BigDecimal("2"), new BigDecimal("8"));
        return new WorkOrderResponse(
                base.workOrderId(), base.companyId(), base.plantId(), base.plantCode(),
                base.workOrderNo(), base.productItemId(), base.productItemCode(),
                base.productItemName(), base.outputUom(), base.bomId(), base.bomRevision(),
                base.bomCapturedAt(), base.sourceRoutingId(), base.sourceRoutingCode(),
                base.sourceRoutingVersion(), base.routingCapturedAt(), base.planningRunId(),
                base.planningRunCode(), base.planningProposalId(), base.outputWarehouseId(),
                base.outputWarehouseCode(), base.plannedQuantity(), base.completedQuantity(),
                base.remainingQuantity(), base.actualGoodQuantity(), base.actualScrapQuantity(),
                base.actualReworkQuantity(), base.availableToReceipt(), base.status(),
                base.plannedStartAt(), base.plannedEndAt(), base.releasedAt(),
                base.executionStartedAt(), base.executionCompletedAt(), base.completedAt(),
                base.cancelledAt(), base.cancelReason(), base.blockedAt(), base.blockReason(),
                base.notes(), base.createdAt(), base.updatedAt(),
                List.of(line), List.of(), List.of(), base.closedAt());
    }

    @Test
    @DisplayName("get: unknown work order returns 404 RESOURCE_NOT_FOUND")
    void getWorkOrder_notFound_returns404() throws Exception {
        when(workOrderService.get(WORK_ORDER_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND,
                        "WorkOrder not found with id: " + WORK_ORDER_ID));

        mockMvc.perform(get("/v1/work-orders/" + WORK_ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("issueComponent: Idempotency-Key header is forwarded to the service unchanged")
    void issueComponent_withIdempotencyKeyHeader_forwardsToService() throws Exception {
        UUID componentLineId = UUID.randomUUID();
        UUID warehouseId     = UUID.randomUUID();
        when(workOrderService.issueComponent(eq(WORK_ORDER_ID), any(WorkOrderComponentIssueRequest.class), eq("KEY-1")))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/component-issues")
                        .header("Idempotency-Key", "KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"componentLineId":"%s","warehouseId":"%s","quantity":5}
                                """.formatted(componentLineId, warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(workOrderService).issueComponent(eq(WORK_ORDER_ID), any(WorkOrderComponentIssueRequest.class), eq("KEY-1"));
    }

    @Test
    @DisplayName("issueComponent: missing Idempotency-Key header forwards null (header is optional)")
    void issueComponent_withoutIdempotencyKeyHeader_forwardsNull() throws Exception {
        UUID componentLineId = UUID.randomUUID();
        UUID warehouseId     = UUID.randomUUID();
        when(workOrderService.issueComponent(eq(WORK_ORDER_ID), any(WorkOrderComponentIssueRequest.class), isNull()))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/component-issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"componentLineId":"%s","warehouseId":"%s","quantity":5}
                                """.formatted(componentLineId, warehouseId)))
                .andExpect(status().isOk());

        verify(workOrderService).issueComponent(eq(WORK_ORDER_ID), any(WorkOrderComponentIssueRequest.class), isNull());
    }

    // ── G8a ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("plan: body (version + component warehouses) and Idempotency-Key reach the service")
    void plan_forwardsTheBodyAndTheIdempotencyKey() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(workOrderService.plan(eq(WORK_ORDER_ID), any(), eq("PLAN-1"))).thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/plan")
                        .header("Idempotency-Key", "PLAN-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedWorkOrderVersion":3,"componentWarehouses":[{"itemId":"%s","warehouseId":"%s"}]}
                                """.formatted(itemId, warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        ArgumentCaptor<WorkOrderPlanRequest> captor = ArgumentCaptor.forClass(WorkOrderPlanRequest.class);
        verify(workOrderService).plan(eq(WORK_ORDER_ID), captor.capture(), eq("PLAN-1"));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().expectedWorkOrderVersion()).isEqualTo(3L);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().componentWarehouses().get(0).warehouseId())
                .isEqualTo(warehouseId);
    }

    @Test
    @DisplayName("PATCH without expectedVersion: 400 naming the field, the service is never called")
    void update_withoutExpectedVersion_returns400NamingTheField() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/v1/work-orders/" + WORK_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes":"x"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));

        verifyNoInteractions(workOrderService);
    }

    @Test
    @DisplayName("schedule-preview: 200 with the preview envelope, body optional")
    void schedulePreview_returns200WithTheEnvelope() throws Exception {
        when(schedulePreviewService.preview(eq(WORK_ORDER_ID), isNull())).thenReturn(new WorkOrderSchedulePreviewResponse(
                WORK_ORDER_ID, 2L, "Asia/Ho_Chi_Minh", "0123456789abcdef", BigDecimal.TEN,
                java.time.Instant.parse("2026-10-20T01:00:00Z"), List.of(), List.of(), java.time.Instant.parse("2026-10-19T01:00:00Z")));

        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/schedule-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.version").value(2))
                .andExpect(jsonPath("$.result.calendarRevision").value("0123456789abcdef"));
    }

    @Test
    @DisplayName("list: a fromDate that is not a date is 400, not 500")
    void list_withAMalformedFromDate_returns400() throws Exception {
        mockMvc.perform(get("/v1/plants/" + PLANT_ID + "/work-orders").param("fromDate", "15/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(workOrderService);
    }
}
