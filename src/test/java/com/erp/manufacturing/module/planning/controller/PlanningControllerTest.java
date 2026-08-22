package com.erp.manufacturing.module.planning.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateRequest;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateResponse;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateSummaryResponse;
import com.erp.manufacturing.module.planning.service.PlanningService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link PlanningController} (`D7b`, group C).
 *
 * <p>A single read-only estimate endpoint. It is a {@code POST} that returns <b>200, not 201</b>
 * (§5.8 applies 201 to <em>create</em>; nothing is persisted here) — asserted because "POST means
 * 201" is the reflex most likely to change it by mistake.
 *
 * <p>The BOM explosion behind it is invariant B25 territory and is covered numerically by
 * {@code PlanningServiceTest}; this test only locks the wire contract.
 */
@WebMvcTest(controllers = PlanningController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PlanningController – response envelope contract")
class PlanningControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PlanningService planningService;

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

    private static final UUID PRODUCT_ITEM_ID = UUID.randomUUID();
    private static final UUID SCOPE_ID        = UUID.randomUUID();
    private static final UUID COMPANY_ID      = UUID.randomUUID();

    private ProductionEstimateResponse sampleResponse() {
        return new ProductionEstimateResponse(
                PRODUCT_ITEM_ID, "FG-100", "Widget", "PLANT", SCOPE_ID, COMPANY_ID,
                new BigDecimal("100.000000"), new BigDecimal("40.000000"),
                List.of(), new ProductionEstimateSummaryResponse(5, 2, false));
    }

    private String estimateBody() {
        return """
                {"productItemId":"%s","scopeType":"PLANT","scopeId":"%s","targetQuantity":100}
                """.formatted(PRODUCT_ITEM_ID, SCOPE_ID);
    }

    @Test
    @DisplayName("estimate: valid request returns 200 (not 201 — nothing is persisted) with the summary")
    void estimateProduction_validRequest_returns200() throws Exception {
        when(planningService.estimateProduction(any(ProductionEstimateRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/planning/production-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(estimateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.productItemCode").value("FG-100"))
                .andExpect(jsonPath("$.result.targetQuantity").value(100.000000))
                .andExpect(jsonPath("$.result.maxBuildableQuantity").value(40.000000))
                .andExpect(jsonPath("$.result.summary.totalLineCount").value(5))
                .andExpect(jsonPath("$.result.summary.shortageLineCount").value(2))
                .andExpect(jsonPath("$.result.summary.feasible").value(false));
    }

    @Test
    @DisplayName("estimate: non-positive targetQuantity returns 400 VALIDATION_ERROR with the field name")
    void estimateProduction_nonPositiveQuantity_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/v1/planning/production-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productItemId":"%s","scopeType":"PLANT","scopeId":"%s","targetQuantity":0}
                                """.formatted(PRODUCT_ITEM_ID, SCOPE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("targetQuantity"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(planningService);
    }

    @Test
    @DisplayName("estimate: product outside the requested scope company stays 422 OPERATION_NOT_ALLOWED "
            + "(§5.3 — cross-company master data)")
    void estimateProduction_productOutsideScopeCompany_returns422() throws Exception {
        when(planningService.estimateProduction(any(ProductionEstimateRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Product item must belong to the requested planning scope company"));

        mockMvc.perform(post("/v1/planning/production-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(estimateBody()))
                .andExpect(status().is(BusinessErrorCode.OPERATION_NOT_ALLOWED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPERATION_NOT_ALLOWED.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("estimate: missing request body returns 400 VALIDATION_ERROR (§5.4 unreadable body)")
    void estimateProduction_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/v1/planning/production-estimates")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(planningService);
    }
}
