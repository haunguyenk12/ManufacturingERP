package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionResponse;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionResult;
import com.erp.manufacturing.module.purchasing.service.PurchaseQualityService;
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

@WebMvcTest(controllers = PurchaseQcController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PlantContextResolver.class)
@DisplayName("PurchaseQcController – wire contract (G7c)")
class PurchaseQcControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PurchaseQualityService purchaseQualityService;

    // Required only so the auto-detected security filters can be built by the slice.
    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID RECEIPT_ID = UUID.randomUUID();
    private static final UUID LINE_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();

    private static PurchaseQcDecisionResult result() {
        PurchaseQcDecisionResponse decision = new PurchaseQcDecisionResponse(UUID.randomUUID(), RECEIPT_ID, "GR-000001", LINE_ID,
                UUID.randomUUID(), "RM", "RM", "KG", "NON_TRACKED", new BigDecimal("20"), new BigDecimal("5"), "bad",
                UUID.randomUUID(), "qc", Instant.parse("2026-10-18T10:00:00Z"), LINE_ID, List.of(), List.of());
        return new PurchaseQcDecisionResult(decision, 3L, new BigDecimal("25"), new BigDecimal("20"), new BigDecimal("5"),
                BigDecimal.ZERO);
    }

    @Test
    @DisplayName("decide: 200 with decision + receiptVersion + pendingQuantity; key and body reach the service")
    void decide_returns200_andForwardsKey() throws Exception {
        when(purchaseQualityService.decide(eq(RECEIPT_ID), eq(LINE_ID), any(PurchaseQcDecisionRequest.class), eq("k-qc")))
                .thenReturn(result());

        mockMvc.perform(post("/v1/goods-receipts/" + RECEIPT_ID + "/lines/" + LINE_ID + "/qc")
                        .header("Idempotency-Key", "k-qc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2,\"acceptedQuantity\":20,\"rejectedQuantity\":5,\"reason\":\"bad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.receiptVersion").value(3))
                .andExpect(jsonPath("$.result.pendingQuantity").value(0))
                .andExpect(jsonPath("$.result.decision.qualityBucketId").value(LINE_ID.toString()))
                .andExpect(jsonPath("$.result.decision.unitPrice").doesNotExist());
        verify(purchaseQualityService).decide(eq(RECEIPT_ID), eq(LINE_ID),
                argThat(r -> r.expectedVersion() == 2L && r.rejectedQuantity().compareTo(new BigDecimal("5")) == 0), eq("k-qc"));
    }

    @Test
    @DisplayName("decide: a seventh decimal or a negative quantity is 400 on the field, before the service")
    void decide_badQuantities_return400() throws Exception {
        mockMvc.perform(post("/v1/goods-receipts/" + RECEIPT_ID + "/lines/" + LINE_ID + "/qc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2,\"acceptedQuantity\":0.1234567,\"rejectedQuantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("acceptedQuantity"));
        mockMvc.perform(post("/v1/goods-receipts/" + RECEIPT_ID + "/lines/" + LINE_ID + "/qc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2,\"acceptedQuantity\":1,\"rejectedQuantity\":-1,\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rejectedQuantity"));
        verifyNoInteractions(purchaseQualityService);
    }

    @Test
    @DisplayName("queue: filters reach the service unsorted; X-Plant-Id mismatch is 409 before it")
    void queue_forwardsFilters_andChecksPlantHeader() throws Exception {
        when(purchaseQualityService.queue(eq(COMPANY_ID), eq(PLANT_ID), isNull(), eq("rm"), isNull(), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(get("/v1/purchase-qc").param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString()).param("search", "rm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
        verify(purchaseQualityService).queue(eq(COMPANY_ID), eq(PLANT_ID), isNull(), eq("rm"), isNull(), isNull(),
                argThat(pageable -> pageable.getSort().isUnsorted()));

        mockMvc.perform(get("/v1/purchase-qc").param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }
}
