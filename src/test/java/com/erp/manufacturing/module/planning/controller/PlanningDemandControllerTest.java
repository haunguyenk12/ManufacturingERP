package com.erp.manufacturing.module.planning.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.planning.dto.PlanningDemandCreateRequest;
import com.erp.manufacturing.module.planning.dto.PlanningDemandResponse;
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link PlanningDemandController} (`D7b`, group C).
 *
 * <p>This controller names {@code plantId} explicitly in both its body and its query string, so
 * §5.6.1 says the {@code X-Plant-Id} cross-check applies — asserted here on both endpoints, because
 * `list` wiring the header is easy to drop in a refactor without any other test noticing.
 *
 * <p>It is also the best place to lock the {@code MethodArgumentTypeMismatchException} handler
 * (§5.4, added in `F1`) against a <b>real</b> controller: until now only
 * {@code GlobalExceptionHandlerTest} covered it, and that uses a stub controller.
 */
@WebMvcTest(controllers = PlanningDemandController.class)
@AutoConfigureMockMvc(addFilters = false)
// R3: PlantContextResolver holds the real header-vs-request comparison, so import the real bean
// instead of mocking it into a no-op.
@Import(PlantContextResolver.class)
@DisplayName("PlanningDemandController – response envelope contract")
class PlanningDemandControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PlanningDemandService planningDemandService;

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

    private static final UUID DEMAND_ID  = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID PLANT_ID   = UUID.randomUUID();
    private static final UUID ITEM_ID    = UUID.randomUUID();

    private PlanningDemandResponse sampleResponse(String statusValue) {
        return new PlanningDemandResponse(
                DEMAND_ID, COMPANY_ID, "COMP-01", PLANT_ID, "PLANT-01",
                ITEM_ID, "FG-100", "Widget", null, null,
                "FORECAST", new BigDecimal("50.000000"), LocalDate.of(2026, 8, 1), 1,
                statusValue, null, null, Instant.now(), Instant.now());
    }

    private String createBody(UUID plantId) {
        return """
                {"companyId":"%s","plantId":"%s","itemId":"%s","demandType":"FORECAST",
                 "requiredQuantity":50,"dueDate":"2026-08-01","priority":1}
                """.formatted(COMPANY_ID, plantId, ITEM_ID);
    }

    @Test
    @DisplayName("create: valid request returns 201 with the OPEN demand")
    void create_validRequest_returns201Created() throws Exception {
        when(planningDemandService.create(any(PlanningDemandCreateRequest.class))).thenReturn(sampleResponse("OPEN"));

        mockMvc.perform(post("/api/v1/planning/demands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(PLANT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.planningDemandId").value(DEMAND_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("OPEN"))
                .andExpect(jsonPath("$.result.requiredQuantity").value(50.000000))
                .andExpect(jsonPath("$.result.dueDate").value("2026-08-01"));
    }

    @Test
    @DisplayName("create: X-Plant-Id disagreeing with the body plantId returns 409 STATE_CONFLICT (§5.6.1)")
    void create_plantHeaderMismatch_returns409BeforeReachingService() throws Exception {
        mockMvc.perform(post("/api/v1/planning/demands")
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(PLANT_ID)))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        // R5: the cross-check must fail before any business logic runs
        verifyNoInteractions(planningDemandService);
    }

    @Test
    @DisplayName("create: missing requiredQuantity returns 400 VALIDATION_ERROR with the field name")
    void create_missingRequiredQuantity_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/planning/demands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","plantId":"%s","itemId":"%s","demandType":"FORECAST",
                                 "dueDate":"2026-08-01"}
                                """.formatted(COMPANY_ID, PLANT_ID, ITEM_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("requiredQuantity"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(planningDemandService);
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(planningDemandService.list(eq(COMPANY_ID), eq(PLANT_ID), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse("OPEN")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/planning/demands")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                // Renamed in F7: spec §6.4 calls this itemSku. The old name must be gone from the
                // wire, not merely joined by a new one.
                .andExpect(jsonPath("$.result.content[0].itemSku").value("FG-100"))
                .andExpect(jsonPath("$.result.content[0].itemCode").doesNotExist())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        verify(planningDemandService).list(eq(COMPANY_ID), eq(PLANT_ID), eq(null), eq(null), eq(null), any());
    }

    @Test
    @DisplayName("list: X-Plant-Id disagreeing with the plantId query param returns 409 STATE_CONFLICT "
            + "(§5.6.1 applies to the query string too, not just the body)")
    void list_plantHeaderMismatch_returns409BeforeReachingService() throws Exception {
        mockMvc.perform(get("/api/v1/planning/demands")
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString())
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString()))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(planningDemandService);
    }

    @Test
    @DisplayName("list: malformed UUID in a query param returns 400 VALIDATION_ERROR, not 500 "
            + "(§5.4 handler, exercised against a real controller for the first time)")
    void list_malformedUuidQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/planning/demands")
                        .param("companyId", "not-a-uuid")
                        .param("plantId", PLANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("companyId"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a valid UUID"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(planningDemandService);
    }

    @Test
    @DisplayName("create: malformed X-Plant-Id header returns 400 FIELD_FORMAT_INVALID (not 409 — a "
            + "header that cannot be parsed is bad input, not a disagreement)")
    void create_malformedPlantHeader_returns400FieldFormatInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/planning/demands")
                        .header(PlantContextResolver.HEADER, "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(PLANT_ID)))
                .andExpect(status().is(ValidationErrorCode.FIELD_FORMAT_INVALID.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.FIELD_FORMAT_INVALID.code()));

        verifyNoInteractions(planningDemandService);
    }

    @Test
    @DisplayName("get: unknown demand returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404() throws Exception {
        when(planningDemandService.get(DEMAND_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND,
                        "Planning demand not found: " + DEMAND_ID));

        mockMvc.perform(get("/api/v1/planning/demands/" + DEMAND_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("cancel: valid request returns 200 with the CANCELLED demand")
    void cancel_openDemand_returns200Cancelled() throws Exception {
        when(planningDemandService.cancel(DEMAND_ID)).thenReturn(sampleResponse("CANCELLED"));

        mockMvc.perform(patch("/api/v1/planning/demands/" + DEMAND_ID + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.status").value("CANCELLED"));
    }

    /**
     * {@code PlanningDemandService.cancel} reads the <em>document status</em>
     * ({@code !demand.isOpen()}), which the table in {@code .claude/rules/error-handling.md} §5.3
     * classifies as 409 {@code STATE_CONFLICT}.
     *
     * <p>Until D11 it answered 422 {@code OPERATION_NOT_ALLOWED}: `D7` swept {@code bom},
     * {@code purchasing}, {@code organization} and {@code sales}, and {@code planning} was never in
     * that scope, so debt #9 was closed for the modules D7 covered but not for this one (debt #26).
     * This test pinned that gap instead of hiding it; D11 closed it, and it now pins the fix.
     */
    @Test
    @DisplayName("cancel: non-OPEN demand returns 409 STATE_CONFLICT (document-status check, §5.3)")
    void cancel_nonOpenDemand_returns409StateConflict() throws Exception {
        when(planningDemandService.cancel(DEMAND_ID))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only OPEN planning demands can be cancelled"));

        mockMvc.perform(patch("/api/v1/planning/demands/" + DEMAND_ID + "/cancel"))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }
}
