package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderDraftCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderReasonRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderResponse;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderUpdateRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderVersionRequest;
import com.erp.manufacturing.module.purchasing.service.PurchaseOrderService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link PurchaseOrderController} (`D7`) – locks the
 * {@code {code,result,message}} envelope (`.claude/rules/error-handling.md` §5.1) and the purchase
 * order state machine's HTTP code, which {@code D7.3} retagged from 422 to 409.
 *
 * <p>Permission checks (`@PreAuthorize`) live in the service layer (C1) and are covered by
 * {@code PurchasingMethodSecurityTest} – not re-tested here.
 */
@WebMvcTest(controllers = PurchaseOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PlantContextResolver.class)
@DisplayName("PurchaseOrderController – response envelope contract")
class PurchaseOrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PurchaseOrderService purchaseOrderService;

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

    private static final UUID COMPANY_ID   = UUID.randomUUID();
    private static final UUID PLANT_ID     = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID  = UUID.randomUUID();
    private static final UUID ORDER_ID     = UUID.randomUUID();

    private PurchaseOrderResponse sampleResponse(String statusValue) {
        return new PurchaseOrderResponse(
                ORDER_ID, COMPANY_ID, "COMP-01", PLANT_ID, "PLANT-01",
                WAREHOUSE_ID, "WH-01", SUPPLIER_ID, "SUP-01", "Acme Supplies",
                "PO-001", statusValue, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15),
                null, null, Instant.now(), Instant.now(), List.of(),
                "PO-001", 3L, "Main warehouse", "REF-1", "VND", false, null,
                null, null, null, null, List.of());
    }

    @Test
    @DisplayName("create: valid request returns 201 with the DRAFT order")
    void create_validRequest_returns201Created() throws Exception {
        when(purchaseOrderService.create(any(PurchaseOrderCreateRequest.class)))
                .thenReturn(sampleResponse("DRAFT"));

        mockMvc.perform(post("/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","plantId":"%s","warehouseId":"%s","supplierId":"%s",
                                 "purchaseOrderNo":"PO-001","orderDate":"2026-07-01","expectedDate":"2026-07-15",
                                 "lines":[{"itemId":"%s","orderedQuantity":10}]}
                                """.formatted(COMPANY_ID, PLANT_ID, WAREHOUSE_ID, SUPPLIER_ID, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.purchaseOrderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("DRAFT"));
    }

    @Test
    @DisplayName("create: empty lines[] fails @Valid before reaching the service")
    void create_withoutLines_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","plantId":"%s","warehouseId":"%s","supplierId":"%s",
                                 "purchaseOrderNo":"PO-001","orderDate":"2026-07-01","expectedDate":"2026-07-15",
                                 "lines":[]}
                                """.formatted(COMPANY_ID, PLANT_ID, WAREHOUSE_ID, SUPPLIER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("lines"));

        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @DisplayName("cancel: order already CONFIRMED (no body) returns 409 STATE_CONFLICT (D7.3 retag, was 422)")
    void cancel_alreadySentOrder_returns409StateConflict() throws Exception {
        when(purchaseOrderService.cancel(ORDER_ID, null, null))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only DRAFT purchase orders can be cancelled"));

        mockMvc.perform(post("/v1/purchase-orders/" + ORDER_ID + "/cancel"))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(purchaseOrderService.list(eq(COMPANY_ID), eq(PLANT_ID), eq(null), eq(null), eq(null),
                eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse("CONFIRMED")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/purchase-orders")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].purchaseOrderNo").value("PO-001"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    // ── G7a ────────────────────────────────────────────────────────────────

    private static final String DRAFT_BODY = """
            {"companyId":"%s","plantId":"%s","warehouseId":"%s","supplierId":"%s",
             "orderDate":"2026-10-14","reference":"TEST-PO-40",
             "lines":[{"itemId":"%s","orderedQuantity":%s,"unitPrice":10,"expectedDate":"2026-10-14"}]}
            """;

    @Test
    @DisplayName("G7a drafts: 201, Idempotency-Key forwarded, G7a fields on the wire")
    void createDraft_returns201AndForwardsTheKey() throws Exception {
        when(purchaseOrderService.createDraft(any(PurchaseOrderDraftCreateRequest.class), eq("key-1")))
                .thenReturn(sampleResponse("DRAFT"));

        mockMvc.perform(post("/v1/purchase-orders/drafts")
                        .header("Idempotency-Key", "key-1")
                        .header(PlantContextResolver.HEADER, PLANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_BODY.formatted(COMPANY_ID, PLANT_ID, WAREHOUSE_ID, SUPPLIER_ID, UUID.randomUUID(), "40")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.code").value("PO-001"))
                .andExpect(jsonPath("$.result.version").value(3))
                .andExpect(jsonPath("$.result.sourceLocked").value(false))
                .andExpect(jsonPath("$.result.history").isArray());
    }

    @Test
    @DisplayName("G7a drafts: X-Plant-Id that differs from body plantId is 409 before the service")
    void createDraft_plantHeaderMismatch_returns409() throws Exception {
        mockMvc.perform(post("/v1/purchase-orders/drafts")
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_BODY.formatted(COMPANY_ID, PLANT_ID, WAREHOUSE_ID, SUPPLIER_ID, UUID.randomUUID(), "40")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @DisplayName("G7a drafts: a seventh decimal is 400 on lines[0].orderedQuantity, never rounded")
    void createDraft_sevenDecimals_returns400() throws Exception {
        mockMvc.perform(post("/v1/purchase-orders/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_BODY.formatted(COMPANY_ID, PLANT_ID, WAREHOUSE_ID, SUPPLIER_ID, UUID.randomUUID(), "1.1234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].orderedQuantity"));

        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @DisplayName("G7a PATCH: expectedVersion is required")
    void update_withoutExpectedVersion_returns400() throws Exception {
        mockMvc.perform(patch("/v1/purchase-orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":"%s","warehouseId":"%s","orderDate":"2026-10-14",
                                 "lines":[{"itemId":"%s","orderedQuantity":1,"expectedDate":"2026-10-14"}]}
                                """.formatted(SUPPLIER_ID, WAREHOUSE_ID, UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));

        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @DisplayName("G7a PATCH: SOURCE_LOCKED is 409 with the locked field")
    void update_sourceLocked_returns409WithField() throws Exception {
        when(purchaseOrderService.updateDraft(eq(ORDER_ID), any(PurchaseOrderUpdateRequest.class), isNull()))
                .thenThrow(ExceptionFactory.withErrors(
                        BusinessErrorCode.SOURCE_LOCKED, Map.of("lines[0].orderedQuantity", "comes from planning")));

        mockMvc.perform(patch("/v1/purchase-orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,"supplierId":"%s","warehouseId":"%s","orderDate":"2026-10-14",
                                 "lines":[{"id":"%s","itemId":"%s","orderedQuantity":2,"expectedDate":"2026-10-14"}]}
                                """.formatted(SUPPLIER_ID, WAREHOUSE_ID, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.SOURCE_LOCKED.code()))
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].orderedQuantity"));
    }

    @Test
    @DisplayName("G7a confirm: body without expectedVersion is 400")
    void confirm_withoutVersion_returns400() throws Exception {
        mockMvc.perform(post("/v1/purchase-orders/" + ORDER_ID + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));

        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @DisplayName("G7a confirm: version and key reach the service")
    void confirm_forwardsVersionAndKey() throws Exception {
        when(purchaseOrderService.confirm(eq(ORDER_ID), any(PurchaseOrderVersionRequest.class), eq("k-conf")))
                .thenReturn(sampleResponse("CONFIRMED"));

        mockMvc.perform(post("/v1/purchase-orders/" + ORDER_ID + "/confirm")
                        .header("Idempotency-Key", "k-conf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));

        verify(purchaseOrderService).confirm(eq(ORDER_ID), argThat(r -> r.expectedVersion() == 2L), eq("k-conf"));
    }

    @Test
    @DisplayName("G7a cancel with a body: reason is required (blank is 400)")
    void cancel_withBlankReason_returns400() throws Exception {
        mockMvc.perform(post("/v1/purchase-orders/" + ORDER_ID + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));

        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @DisplayName("G7a close: body is required")
    void close_withoutBody_returns400() throws Exception {
        mockMvc.perform(post("/v1/purchase-orders/" + ORDER_ID + "/close"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @DisplayName("G7a close: reason and version reach the service")
    void close_forwardsTheRequest() throws Exception {
        when(purchaseOrderService.close(eq(ORDER_ID), any(PurchaseOrderReasonRequest.class), isNull()))
                .thenReturn(sampleResponse("CLOSED"));

        mockMvc.perform(post("/v1/purchase-orders/" + ORDER_ID + "/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":4,\"reason\":\"Supplier stopped the item\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CLOSED"));

        verify(purchaseOrderService).close(eq(ORDER_ID),
                argThat(r -> r.expectedVersion() == 4L && r.reason().equals("Supplier stopped the item")), isNull());
    }

    @Test
    @DisplayName("G7a list: search, status and inclusive order-date range reach the service")
    void list_forwardsSearchAndDateRange() throws Exception {
        when(purchaseOrderService.list(eq(COMPANY_ID), eq(PLANT_ID), eq(null), eq(null), eq(PurchaseOrderStatus.CLOSED),
                eq("acme"), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(get("/v1/purchase-orders")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString())
                        .param("status", "CLOSED")
                        .param("search", "acme")
                        .param("orderDateFrom", "2026-10-01")
                        .param("orderDateTo", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    @DisplayName("G7a list: the legacy status SENT no longer exists — 400, not an empty page")
    void list_legacySentStatus_returns400() throws Exception {
        mockMvc.perform(get("/v1/purchase-orders")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString())
                        .param("status", "SENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));

        verifyNoInteractions(purchaseOrderService);
    }
}

