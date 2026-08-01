package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderResponse;
import com.erp.manufacturing.module.purchasing.service.PurchaseOrderService;
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
import java.time.LocalDate;
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
                null, null, Instant.now(), Instant.now(), List.of());
    }

    @Test
    @DisplayName("create: valid request returns 201 with the DRAFT order")
    void create_validRequest_returns201Created() throws Exception {
        when(purchaseOrderService.create(any(PurchaseOrderCreateRequest.class)))
                .thenReturn(sampleResponse("DRAFT"));

        mockMvc.perform(post("/api/v1/purchase-orders")
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
        mockMvc.perform(post("/api/v1/purchase-orders")
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
    @DisplayName("cancel: order already SENT returns 409 STATE_CONFLICT (D7.3 retag, was 422)")
    void cancel_alreadySentOrder_returns409StateConflict() throws Exception {
        when(purchaseOrderService.cancel(ORDER_ID))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only DRAFT purchase orders can be cancelled"));

        mockMvc.perform(post("/api/v1/purchase-orders/" + ORDER_ID + "/cancel"))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(purchaseOrderService.list(eq(COMPANY_ID), eq(PLANT_ID), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse("SENT")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/purchase-orders")
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
}
