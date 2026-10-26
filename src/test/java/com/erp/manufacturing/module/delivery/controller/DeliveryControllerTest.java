package com.erp.manufacturing.module.delivery.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.delivery.service.DeliveryQueryService;
import com.erp.manufacturing.module.delivery.service.DeliveryService;
import com.erp.manufacturing.module.sales.dto.CommercialCommandResult;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G10b: envelope, validation and the {@code X-Plant-Id} cross-check of the delivery endpoints. */
@WebMvcTest(controllers = DeliveryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PlantContextResolver.class)
@DisplayName("DeliveryController – response envelope contract")
class DeliveryControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean DeliveryService deliveryService;
    @MockBean DeliveryQueryService queryService;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();

    @Test
    @DisplayName("create: 201 with the command result only, Idempotency-Key forwarded")
    void create_returns201WithTheCommandResult() throws Exception {
        when(deliveryService.create(any(), eq("k-1"))).thenReturn(CommercialCommandResult.of(DELIVERY_ID, "DELIVERY", 0L));

        mockMvc.perform(post("/v1/deliveries").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body("3")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.documentId").value(DELIVERY_ID.toString()))
                .andExpect(jsonPath("$.result.type").value("DELIVERY"))
                .andExpect(jsonPath("$.result.version").value(0))
                .andExpect(jsonPath("$.result.pricingVersion").doesNotExist());
    }

    @Test
    @DisplayName("create: zero, a seventh decimal or 101 lines is a 400 on the field, before the service")
    void create_invalidQuantityOrTooManyLines_returns400() throws Exception {
        for (String quantity : List.of("0", "1.1234567", "-2")) {
            mockMvc.perform(post("/v1/deliveries").header("Idempotency-Key", "k")
                            .contentType(MediaType.APPLICATION_JSON).content(body(quantity)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == 'lines[0].quantity')]").exists());
        }
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            lines.append(i == 0 ? "" : ",").append("{\"salesOrderLineId\":\"%s\",\"sourceKey\":\"x\",\"quantity\":1}"
                    .formatted(UUID.randomUUID()));
        }
        mockMvc.perform(post("/v1/deliveries").header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salesOrderId\":\"%s\",\"expectedOrderVersion\":1,\"documentDate\":\"2026-10-20\",\"lines\":[%s]}"
                                .formatted(ORDER_ID, lines)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'lines')]").exists());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @DisplayName("post: a quota overrun keeps its code and field (FE branches on the code, not the 409)")
    void post_quotaExceeded_returns409WithTheCode() throws Exception {
        when(deliveryService.post(eq(DELIVERY_ID), any(), eq("k-2"))).thenThrow(
                new AppException(BusinessErrorCode.SOURCE_UNKNOWN));

        mockMvc.perform(post("/v1/deliveries/" + DELIVERY_ID + "/post").header("Idempotency-Key", "k-2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.SOURCE_UNKNOWN.code()));
    }

    @Test
    @DisplayName("list: X-Plant-Id that disagrees with plantId is 409 before the service")
    void list_plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(get("/v1/deliveries").param("plantId", UUID.randomUUID().toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("cancel: a missing reason is a 400 on reason")
    void cancel_withoutReason_returns400() throws Exception {
        mockMvc.perform(post("/v1/deliveries/" + DELIVERY_ID + "/cancel").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'reason')]").exists());
        verifyNoInteractions(deliveryService);
    }

    private static String body(String quantity) {
        return ("{\"salesOrderId\":\"%s\",\"expectedOrderVersion\":1,\"documentDate\":\"2026-10-20\","
                + "\"lines\":[{\"salesOrderLineId\":\"%s\",\"sourceKey\":\"abc\",\"quantity\":%s}]}")
                .formatted(ORDER_ID, UUID.randomUUID(), quantity);
    }
}
