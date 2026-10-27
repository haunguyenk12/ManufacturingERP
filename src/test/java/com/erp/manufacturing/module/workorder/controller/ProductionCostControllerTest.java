package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Checklist;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Readiness;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.RunCommandResult;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Section;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateCommandResult;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostBaselineService;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostCloseService;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostRateService;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G11: production cost endpoints — envelope, validation, header forwarding, {@code X-Plant-Id} on the plant-scoped ones,
 * and the absence (not {@code null}) of money in an operational checklist (FE R27, E6).
 */
@WebMvcTest(controllers = ProductionCostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PlantContextResolver.class)
@DisplayName("ProductionCostController – response envelope contract")
class ProductionCostControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ProductionCostRateService rateService;
    @MockBean ProductionCostBaselineService baselineService;
    @MockBean ProductionCostService costService;
    @MockBean ProductionCostCloseService closeService;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID WORK_CENTER_ID = UUID.randomUUID();
    private static final UUID WORK_ORDER_ID = UUID.randomUUID();

    @Test
    void saveRate_forwardsTheKey_andReturnsIdentityAndVersion() throws Exception {
        when(rateService.save(eq(PLANT_ID), eq(WORK_CENTER_ID), any(), eq("RATE-1")))
                .thenReturn(new RateCommandResult(WORK_CENTER_ID, 1));

        mockMvc.perform(put("/v1/plants/{p}/production-cost-rates/{w}", PLANT_ID, WORK_CENTER_ID)
                        .header("Idempotency-Key", "RATE-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"laborRate\":6000,\"overheadRate\":3000,\"reason\":\"Operating rate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.workCenterId").value(WORK_CENTER_ID.toString()))
                .andExpect(jsonPath("$.result.rateVersion").value(1));
    }

    /** FE R03: a rate with 7 decimals is a field error; the service is never called. */
    @Test
    void saveRate_sevenDecimals_is400OnTheField() throws Exception {
        mockMvc.perform(put("/v1/plants/{p}/production-cost-rates/{w}", PLANT_ID, WORK_CENTER_ID)
                        .header("Idempotency-Key", "RATE-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"laborRate\":1.1234567,\"overheadRate\":3000,\"reason\":\"Operating rate\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("laborRate"));
        verifyNoInteractions(rateService);
    }

    @Test
    void listRates_plantHeaderMismatch_is409() throws Exception {
        mockMvc.perform(get("/v1/plants/{p}/production-cost-rates", PLANT_ID)
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
        verifyNoInteractions(rateService);
    }

    @Test
    void listCosts_unknownMode_is400() throws Exception {
        mockMvc.perform(get("/v1/plants/{p}/production-costs", PLANT_ID).param("mode", "everything"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("mode"));
        verifyNoInteractions(costService);
    }

    /** FE writes sections in lower case. */
    @Test
    void getCost_lowerCaseSection_isAccepted() throws Exception {
        mockMvc.perform(get("/v1/work-orders/{id}/production-costs", WORK_ORDER_ID).param("section", "allocation"))
                .andExpect(status().isOk());
        verify(costService).get(WORK_ORDER_ID, Section.ALLOCATION, 0, 20);
    }

    @Test
    void checklist_withoutCostView_omitsSourceVersionAndCost() throws Exception {
        when(costService.checklist(WORK_ORDER_ID)).thenReturn(new Checklist(WORK_ORDER_ID, "WO-1", WorkOrderStatus.COMPLETED,
                20L, Readiness.READY, false, List.of(), null, List.of(), null, null, null));

        mockMvc.perform(get("/v1/work-orders/{id}/cost-checklist", WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.moneyVisible").value(false))
                .andExpect(jsonPath("$.result.sourceVersion").doesNotExist())
                .andExpect(jsonPath("$.result.cost").doesNotExist())
                .andExpect(jsonPath("$.result.closed").value((Object) null));
    }

    @Test
    void finalize_staleSource_is409CostSourceStale() throws Exception {
        when(closeService.finalizeAndClose(eq(WORK_ORDER_ID), any(), eq("FIN-1"))).thenThrow(ExceptionFactory.withErrors(
                BusinessErrorCode.COST_SOURCE_STALE, Map.of("expectedSourceVersion", "stale")));

        mockMvc.perform(post("/v1/work-orders/{id}/finalize-cost-and-close", WORK_ORDER_ID)
                        .header("Idempotency-Key", "FIN-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":20,\"expectedSourceVersion\":\"cs1-a\",\"runId\":\""
                                + UUID.randomUUID() + "\",\"expectedRunVersion\":1,\"reason\":\"Reconciled\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.COST_SOURCE_STALE.code()))
                .andExpect(jsonPath("$.errors[0].field").value("expectedSourceVersion"));
    }

    @Test
    void createRun_missingSourceVersion_is400() throws Exception {
        mockMvc.perform(post("/v1/work-orders/{id}/cost-runs", WORK_ORDER_ID)
                        .header("Idempotency-Key", "RUN-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expectedSourceVersion"));
        verify(costService, never()).createRun(any(), any(), any());
    }

    @Test
    void createRun_returnsTheRunIdentity() throws Exception {
        UUID runId = UUID.randomUUID();
        when(costService.createRun(eq(WORK_ORDER_ID), any(), eq("RUN-1")))
                .thenReturn(new RunCommandResult(WORK_ORDER_ID, runId, 1, 20L));

        mockMvc.perform(post("/v1/work-orders/{id}/cost-runs", WORK_ORDER_ID)
                        .header("Idempotency-Key", "RUN-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":20,\"expectedSourceVersion\":\"cs1-a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.runId").value(runId.toString()))
                .andExpect(jsonPath("$.result.runVersion").value(1))
                .andExpect(jsonPath("$.result.workOrderVersion").value(20));
    }
}
