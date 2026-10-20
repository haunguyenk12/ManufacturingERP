package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.workorder.domain.MaterialReturnStatus;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnLineResponse;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipOutputResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipSection;
import com.erp.manufacturing.module.workorder.dto.wip.WorkOrderWipResponse;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReturnService;
import com.erp.manufacturing.module.workorder.service.execution.WorkOrderWipService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G8d: WIP and material return endpoints — envelope, validation, header forwarding, X-Plant-Id on the plant lists. */
@WebMvcTest(controllers = WorkInProgressController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PlantContextResolver.class)
@DisplayName("WorkInProgressController – response envelope contract")
class WorkInProgressControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean WorkOrderWipService wipService;
    @MockBean MaterialReturnService materialReturnService;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID WORK_ORDER_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID ISSUE_LINE_ID = UUID.randomUUID();

    private static MaterialReturnResponse sampleReturn() {
        return new MaterialReturnResponse(UUID.randomUUID(), "MR-0A1B2C3D", WORK_ORDER_ID, "WO-1", MaterialReturnStatus.POSTED,
                "Unused after the run", Instant.parse("2026-10-20T03:00:00Z"), UUID.randomUUID(), "manager", "trace", 12L,
                List.of(new MaterialReturnLineResponse(UUID.randomUUID(), 1, ISSUE_LINE_ID, UUID.randomUUID(), "MI-1",
                        UUID.randomUUID(), UUID.randomUUID(), "RM-1", "KG", UUID.randomUUID(), "RAW", null, null,
                        new BigDecimal("2.000000"), UUID.randomUUID())));
    }

    @Test
    void postReturn_returns201AndForwardsTheIdempotencyKey() throws Exception {
        when(materialReturnService.post(eq(WORK_ORDER_ID), any(), eq("KEY-1"))).thenReturn(sampleReturn());

        mockMvc.perform(post("/v1/work-orders/{workOrderId}/material-returns", WORK_ORDER_ID)
                        .header("Idempotency-Key", "KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedWorkOrderVersion\":11,\"reason\":\"Unused after the run\","
                                + "\"lines\":[{\"sourceIssueLineId\":\"" + ISSUE_LINE_ID + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.code").value("MR-0A1B2C3D"))
                .andExpect(jsonPath("$.result.status").value("POSTED"))
                .andExpect(jsonPath("$.result.workOrderVersion").value(12))
                .andExpect(jsonPath("$.result.lines[0].sourceIssueLineId").value(ISSUE_LINE_ID.toString()));

        verify(materialReturnService).post(eq(WORK_ORDER_ID), argThat(request ->
                request.expectedWorkOrderVersion() == 11L && request.lines().size() == 1), eq("KEY-1"));
    }

    @Test
    void postReturn_withoutVersionOrLines_is400BeforeTheService() throws Exception {
        mockMvc.perform(post("/v1/work-orders/{workOrderId}/material-returns", WORK_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Unused after the run\",\"lines\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(materialReturnService);
    }

    @Test
    void postReturn_seventhDecimal_is400OnTheLineQuantity() throws Exception {
        mockMvc.perform(post("/v1/work-orders/{workOrderId}/material-returns", WORK_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedWorkOrderVersion\":1,\"reason\":\"Unused after the run\","
                                + "\"lines\":[{\"sourceIssueLineId\":\"" + ISSUE_LINE_ID + "\",\"quantity\":1.0000001}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].quantity"));

        verifyNoInteractions(materialReturnService);
    }

    @Test
    void listReturns_plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(get("/v1/material-returns")
                        .param("plantId", PLANT_ID.toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(materialReturnService);
    }

    @Test
    void listReturns_returnsThePageEnvelope() throws Exception {
        when(materialReturnService.listByPlant(eq(PLANT_ID), isNull(), eq("MR-0A"), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(sampleReturn()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/material-returns").param("plantId", PLANT_ID.toString()).param("search", "MR-0A")
                        .param("fromDate", "2026-10-01").param("toDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].code").value("MR-0A1B2C3D"))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20));
    }

    @Test
    void listWip_plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(get("/v1/plants/{plantId}/wip", PLANT_ID)
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict());

        verifyNoInteractions(wipService);
    }

    @Test
    void getWip_forwardsTheSection() throws Exception {
        when(wipService.get(WORK_ORDER_ID, WipSection.AWAITING_RECEIPT)).thenReturn(new WorkOrderWipResponse(WORK_ORDER_ID,
                "WO-1", WorkOrderStatus.COMPLETED, 13L, true, true, WipSection.AWAITING_RECEIPT, null, null, null,
                new WipOutputResponse("EA", BigDecimal.TEN, new BigDecimal("9"), BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("9"), BigDecimal.ONE)));

        mockMvc.perform(get("/v1/work-orders/{workOrderId}/wip", WORK_ORDER_ID).param("section", "AWAITING_RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.section").value("AWAITING_RECEIPT"))
                .andExpect(jsonPath("$.result.materials").doesNotExist())
                .andExpect(jsonPath("$.result.output.finalGoodQuantity").value(9))
                .andExpect(jsonPath("$.result.output.terminalShortfallQuantity").value(1));
    }

    /** HISTORY is not a WIP section in this release — the documents' own lists carry the history. */
    @Test
    void getWip_unknownSection_is400() throws Exception {
        mockMvc.perform(get("/v1/work-orders/{workOrderId}/wip", WORK_ORDER_ID).param("section", "HISTORY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(wipService);
    }
}
