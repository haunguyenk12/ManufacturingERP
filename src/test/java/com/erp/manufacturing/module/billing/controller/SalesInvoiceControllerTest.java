package com.erp.manufacturing.module.billing.controller;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewLineResponse;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewResponse;
import com.erp.manufacturing.module.billing.service.SalesInvoiceQueryService;
import com.erp.manufacturing.module.billing.service.SalesInvoiceService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G10c: envelope, validation, money on the wire and the {@code X-Plant-Id} cross-check of the invoice endpoints. */
@WebMvcTest(controllers = SalesInvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PlantContextResolver.class)
@DisplayName("SalesInvoiceController – response envelope contract")
class SalesInvoiceControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean SalesInvoiceService invoiceService;
    @MockBean SalesInvoiceQueryService queryService;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();

    @Test
    @DisplayName("create: 201 with the command result only, Idempotency-Key forwarded")
    void create_returns201WithTheCommandResult() throws Exception {
        when(invoiceService.create(any(), eq("k-1"))).thenReturn(CommercialCommandResult.of(INVOICE_ID, "INVOICE", 0L));

        mockMvc.perform(post("/v1/sales-invoices").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("3", "2026-10-30")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.documentId").value(INVOICE_ID.toString()))
                .andExpect(jsonPath("$.result.type").value("INVOICE"))
                .andExpect(jsonPath("$.result.version").value(0));
    }

    @Test
    @DisplayName("preview: amounts are whole-dong JSON numbers")
    void preview_returnsWholeDongNumbers() throws Exception {
        UUID line = UUID.randomUUID();
        when(invoiceService.preview(any())).thenReturn(new SalesInvoicePreviewResponse(ORDER_ID, "VND", "PRICE-1",
                "BILL-0123456789abcdef", new BigDecimal("800000"), List.of(new SalesInvoicePreviewLineResponse(line,
                UUID.randomUUID(), new BigDecimal("8"), new BigDecimal("100000"), new BigDecimal("800000"),
                BigDecimal.ZERO, BigDecimal.ZERO))));

        mockMvc.perform(post("/v1/sales-invoices/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"salesOrderId\":\"%s\",\"expectedOrderVersion\":1,"
                                + "\"lines\":[{\"deliveryLineId\":\"%s\",\"quantity\":8}]}").formatted(ORDER_ID, line)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.amount").value(800000))
                .andExpect(jsonPath("$.result.currency").value("VND"))
                .andExpect(jsonPath("$.result.billingSnapshotVersion").value("BILL-0123456789abcdef"))
                .andExpect(jsonPath("$.result.lines[0].amount").value(800000));
    }

    @Test
    @DisplayName("create: zero, a seventh decimal or a missing due date is a 400 on the field, before the service")
    void create_invalidInput_returns400() throws Exception {
        for (String quantity : List.of("0", "1.1234567", "-2")) {
            mockMvc.perform(post("/v1/sales-invoices").header("Idempotency-Key", "k")
                            .contentType(MediaType.APPLICATION_JSON).content(createBody(quantity, "2026-10-30")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == 'lines[0].quantity')]").exists());
        }
        mockMvc.perform(post("/v1/sales-invoices").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("1", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'dueDate')]").exists());
        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("post: PRICE_STALE keeps its code (FE re-previews on it, not on the 409)")
    void post_priceStale_returns409WithTheCode() throws Exception {
        when(invoiceService.post(eq(INVOICE_ID), any(), eq("k-2"))).thenThrow(
                ExceptionFactory.withErrors(BusinessErrorCode.PRICE_STALE, Map.of("billingSnapshotVersion", "moved")));

        mockMvc.perform(post("/v1/sales-invoices/" + INVOICE_ID + "/post").header("Idempotency-Key", "k-2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.PRICE_STALE.code()))
                .andExpect(jsonPath("$.errors[0].field").value("billingSnapshotVersion"));
    }

    @Test
    @DisplayName("list: X-Plant-Id that disagrees with plantId is 409 before the service")
    void list_plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(get("/v1/sales-invoices").param("plantId", UUID.randomUUID().toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
        verifyNoInteractions(queryService);
    }

    private static String createBody(String quantity, String dueDate) {
        return ("{\"salesOrderId\":\"%s\",\"expectedOrderVersion\":1,\"billingSnapshotVersion\":\"BILL-x\","
                + "\"documentDate\":\"2026-10-20\"" + (dueDate == null ? "" : ",\"dueDate\":\"" + dueDate + "\"")
                + ",\"lines\":[{\"deliveryLineId\":\"%s\",\"quantity\":%s}]}")
                .formatted(ORDER_ID, UUID.randomUUID(), quantity);
    }
}
