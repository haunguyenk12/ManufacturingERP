package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptDraftCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptPostRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptVersionRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptResponse;
import com.erp.manufacturing.module.purchasing.service.GoodsReceiptService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link GoodsReceiptController} (`D7`) – locks the {@code {code,result,message}}
 * envelope (`.claude/rules/error-handling.md` §5.1), the Idempotency-Key header forwarding
 * (rule `R9`) and the two purchasing codes {@code D7.3} retagged from 422 to 409.
 *
 * <p>Dedup logic itself lives in {@code GoodsReceiptService} (bất biến B27/B28) and is covered by
 * {@code GoodsReceiptServiceTest} – here only the wire contract is asserted.
 */
@WebMvcTest(controllers = GoodsReceiptController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(com.erp.manufacturing.common.web.PlantContextResolver.class)
@DisplayName("GoodsReceiptController – response envelope contract")
class GoodsReceiptControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    GoodsReceiptService goodsReceiptService;
    @MockBean
    com.erp.manufacturing.module.purchasing.service.GoodsReceiptDraftService goodsReceiptDraftService;

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

    private static final UUID ORDER_ID      = UUID.randomUUID();
    private static final UUID ORDER_LINE_ID = UUID.randomUUID();
    private static final UUID RECEIPT_ID    = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID  = UUID.randomUUID();

    private GoodsReceiptResponse sampleResponse() {
        return new GoodsReceiptResponse(
                RECEIPT_ID, ORDER_ID, WAREHOUSE_ID, "WH-01", "GR-001", "POSTED",
                Instant.now(), "GR-KEY", null, null, null,
                Instant.now(), Instant.now(), List.of());
    }

    private String postBody(String quantity) {
        return """
                {"receiptNo":"GR-001","lines":[{"purchaseOrderLineId":"%s","receivedQuantity":%s}]}
                """.formatted(ORDER_LINE_ID, quantity);
    }

    @Test
    @DisplayName("post: Idempotency-Key header reaches the service unchanged and returns 201")
    void post_withIdempotencyKeyHeader_forwardsHeaderAndReturns201() throws Exception {
        when(goodsReceiptService.post(eq(ORDER_ID), any(GoodsReceiptPostRequest.class), eq("GR-KEY")))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/purchase-orders/" + ORDER_ID + "/goods-receipts")
                        .header("Idempotency-Key", "GR-KEY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.goodsReceiptId").value(RECEIPT_ID.toString()))
                .andExpect(jsonPath("$.result.idempotencyKey").value("GR-KEY"));

        verify(goodsReceiptService).post(eq(ORDER_ID), any(GoodsReceiptPostRequest.class), eq("GR-KEY"));
    }

    @Test
    @DisplayName("post: same Idempotency-Key with a different payload returns 409 IDEMPOTENCY_CONFLICT")
    void post_sameKeyDifferentPayload_returns409IdempotencyConflict() throws Exception {
        when(goodsReceiptService.post(eq(ORDER_ID), any(GoodsReceiptPostRequest.class), eq("GR-KEY")))
                .thenThrow(new AppException(BusinessErrorCode.IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key was already used with a different payload"));

        mockMvc.perform(post("/v1/purchase-orders/" + ORDER_ID + "/goods-receipts")
                        .header("Idempotency-Key", "GR-KEY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("7")))
                .andExpect(status().is(BusinessErrorCode.IDEMPOTENCY_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.IDEMPOTENCY_CONFLICT.code()));
    }

    @Test
    @DisplayName("post: over-receipt returns 409 PLANNED_QUANTITY_EXCEEDED (B27, D7.3 retag from 422)")
    void post_overRemainingOrderedQuantity_returns409PlannedQuantityExceeded() throws Exception {
        when(goodsReceiptService.post(eq(ORDER_ID), any(GoodsReceiptPostRequest.class), isNull()))
                .thenThrow(new AppException(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                        "Received quantity cannot exceed remaining ordered quantity"));

        mockMvc.perform(post("/v1/purchase-orders/" + ORDER_ID + "/goods-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("999")))
                .andExpect(status().is(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED.code()));
    }

    // ── G7b ────────────────────────────────────────────────────────────────

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();

    @Test
    @DisplayName("G7b drafts: 201 DRAFT, key forwarded")
    void createDraft_returns201() throws Exception {
        when(goodsReceiptDraftService.createDraft(any(GoodsReceiptDraftCreateRequest.class), eq("k-1"))).thenReturn(sampleResponse());

        mockMvc.perform(post("/v1/goods-receipts")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purchaseOrderId":"%s","receivedDate":"2026-10-18",
                                 "lines":[{"purchaseOrderLineId":"%s","receivedQuantity":25}]}
                                """.formatted(ORDER_ID, ORDER_LINE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.goodsReceiptId").value(RECEIPT_ID.toString()));
    }

    @Test
    @DisplayName("G7b drafts: a seventh decimal is 400 on lines[0].receivedQuantity")
    void createDraft_sevenDecimals_returns400() throws Exception {
        mockMvc.perform(post("/v1/goods-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purchaseOrderId":"%s","receivedDate":"2026-10-18",
                                 "lines":[{"purchaseOrderLineId":"%s","receivedQuantity":1.1234567}]}
                                """.formatted(ORDER_ID, ORDER_LINE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].receivedQuantity"));
        verifyNoInteractions(goodsReceiptDraftService);
    }

    @Test
    @DisplayName("G7b post: expectedVersion required; version + key reach the service")
    void post_requiresVersion_andForwardsIt() throws Exception {
        mockMvc.perform(post("/v1/goods-receipts/" + RECEIPT_ID + "/post")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));

        when(goodsReceiptDraftService.post(eq(RECEIPT_ID), any(GoodsReceiptVersionRequest.class), eq("k-p"))).thenReturn(sampleResponse());
        mockMvc.perform(post("/v1/goods-receipts/" + RECEIPT_ID + "/post")
                        .header("Idempotency-Key", "k-p")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        verify(goodsReceiptDraftService).post(eq(RECEIPT_ID), argThat(r -> r.expectedVersion() == 1L), eq("k-p"));
    }

    @Test
    @DisplayName("G7b list: filters reach the service; X-Plant-Id mismatch is 409 before it")
    void list_forwardsFilters_andChecksPlantHeader() throws Exception {
        when(goodsReceiptDraftService.list(eq(COMPANY_ID), eq(PLANT_ID), eq(null), eq(null),
                eq(com.erp.manufacturing.module.purchasing.domain.GoodsReceiptStatus.DRAFT), eq("gr"),
                eq(java.time.LocalDate.of(2026, 10, 1)), eq(null), any()))
                .thenReturn(new com.erp.manufacturing.common.response.PageResult<>(List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(get("/v1/goods-receipts").param("companyId", COMPANY_ID.toString()).param("plantId", PLANT_ID.toString())
                        .param("status", "DRAFT").param("search", "gr").param("receivedDateFrom", "2026-10-01"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/goods-receipts").param("companyId", COMPANY_ID.toString()).param("plantId", PLANT_ID.toString())
                        .header(com.erp.manufacturing.common.web.PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("G7b candidates: static path is not taken for a receipt id")
    void candidates_routeBeforeTheIdPath() throws Exception {
        when(goodsReceiptDraftService.candidates(eq(COMPANY_ID), eq(PLANT_ID), isNull(), any()))
                .thenReturn(new com.erp.manufacturing.common.response.PageResult<>(List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(get("/v1/goods-receipts/purchase-order-candidates")
                        .param("companyId", COMPANY_ID.toString()).param("plantId", PLANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
        verifyNoInteractions(goodsReceiptService);
    }

    @Test
    @DisplayName("cancel: receipt that is not POSTED returns 409 STATE_CONFLICT (D7.3 retag, was 422)")
    void cancel_receiptNotPosted_returns409StateConflict() throws Exception {
        when(goodsReceiptService.cancel(eq(RECEIPT_ID), any(), isNull()))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only POSTED goods receipts can be cancelled"));

        mockMvc.perform(post("/v1/goods-receipts/" + RECEIPT_ID + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cancelNote":"wrong supplier"}
                                """))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }
}
