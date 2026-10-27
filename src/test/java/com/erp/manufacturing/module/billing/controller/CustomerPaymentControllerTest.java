package com.erp.manufacturing.module.billing.controller;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.billing.service.CustomerPaymentQueryService;
import com.erp.manufacturing.module.billing.service.CustomerPaymentService;
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

/** G10d: envelope, money validation and the {@code X-Plant-Id} cross-check of the payment endpoints. */
@WebMvcTest(controllers = CustomerPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PlantContextResolver.class)
@DisplayName("CustomerPaymentController – response envelope contract")
class CustomerPaymentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean CustomerPaymentService paymentService;
    @MockBean CustomerPaymentQueryService queryService;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID PAYMENT_ID = UUID.randomUUID();

    @Test
    @DisplayName("create: 201 with the command result only, Idempotency-Key forwarded")
    void create_returns201WithTheCommandResult() throws Exception {
        when(paymentService.create(any(), eq("k-1"))).thenReturn(CommercialCommandResult.of(PAYMENT_ID, "PAYMENT", 0L));

        mockMvc.perform(post("/v1/customer-payments").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body("300000", "300000", "TRANSFER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.documentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.result.type").value("PAYMENT"));
    }

    @Test
    @DisplayName("create: a fraction of a dong, zero, or an unknown method is a 400 on the field, before the service")
    void create_invalidMoneyOrMethod_returns400() throws Exception {
        for (String amount : List.of("0.5", "0", "-1")) {
            mockMvc.perform(post("/v1/customer-payments").header("Idempotency-Key", "k")
                            .contentType(MediaType.APPLICATION_JSON).content(body(amount, "1", "CASH")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists());
        }
        mockMvc.perform(post("/v1/customer-payments").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body("1", "0.5", "CASH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'allocations[0].amount')]").exists());
        mockMvc.perform(post("/v1/customer-payments").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body("1", "1", "CHEQUE")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("list and invoice candidates: X-Plant-Id that disagrees with plantId is 409 before the service")
    void plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(get("/v1/customer-payments").param("plantId", UUID.randomUUID().toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
        mockMvc.perform(get("/v1/customer-payments/invoice-candidates").param("plantId", UUID.randomUUID().toString())
                        .param("customerId", UUID.randomUUID().toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict());
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("cancel: a missing reason is a 400 on reason")
    void cancel_withoutReason_returns400() throws Exception {
        mockMvc.perform(post("/v1/customer-payments/" + PAYMENT_ID + "/cancel").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'reason')]").exists());
        verifyNoInteractions(paymentService);
    }

    private static String body(String amount, String allocated, String method) {
        return ("{\"customerId\":\"%s\",\"documentDate\":\"2026-10-26\",\"method\":\"%s\",\"amount\":%s,"
                + "\"allocations\":[{\"invoiceId\":\"%s\",\"expectedInvoiceVersion\":1,\"amount\":%s}]}")
                .formatted(UUID.randomUUID(), method, amount, UUID.randomUUID(), allocated);
    }
}
