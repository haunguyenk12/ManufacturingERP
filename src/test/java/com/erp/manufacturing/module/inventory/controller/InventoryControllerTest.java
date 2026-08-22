package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.inventory.dto.StockBalanceResponse;
import com.erp.manufacturing.module.inventory.dto.StockIssueRequest;
import com.erp.manufacturing.module.inventory.dto.StockMovementResponse;
import com.erp.manufacturing.module.inventory.dto.StockReceiveRequest;
import com.erp.manufacturing.module.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link InventoryController} – locks the {@code {code,result,message}} envelope
 * and the Idempotency-Key header forwarding pattern (`.claude/rules/error-handling.md` §5.1,
 * `NEXT_PHASE_PLAN.md` T2 §4.4). {@code InventoryService} is mocked, so real dedup logic (bất biến
 * B5, `module/inventory/CLAUDE.md`) is NOT re-tested here (D4) – only that the header reaches the
 * service unchanged and that the response envelope stays consistent across repeated calls.
 */
@WebMvcTest(controllers = InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InventoryController – response envelope contract")
class InventoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    InventoryService inventoryService;

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

    private static final UUID ITEM_ID      = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    private StockMovementResponse movementResponse(String idempotencyKey) {
        return new StockMovementResponse(
                UUID.randomUUID(), ITEM_ID, WAREHOUSE_ID, null, null,
                "RECEIPT", "IN", BigDecimal.TEN, "reason", "PO", "PO-001",
                idempotencyKey, Instant.now());
    }

    @Test
    @DisplayName("receive: valid request returns 200 with the movement result")
    void receive_validRequest_returns200WithMovementResult() throws Exception {
        when(inventoryService.receive(any(StockReceiveRequest.class), eq("KEY-1")))
                .thenReturn(movementResponse("KEY-1"));

        mockMvc.perform(post("/v1/inventory/receive")
                        .header("Idempotency-Key", "KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"%s","warehouseId":"%s","quantity":10}
                                """.formatted(ITEM_ID, WAREHOUSE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.itemId").value(ITEM_ID.toString()));
    }

    @Test
    @DisplayName("receive: missing Idempotency-Key header still succeeds (header is optional)")
    void receive_missingIdempotencyKeyHeader_stillSucceeds() throws Exception {
        when(inventoryService.receive(any(StockReceiveRequest.class), eq(null)))
                .thenReturn(movementResponse(null));

        mockMvc.perform(post("/v1/inventory/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"%s","warehouseId":"%s","quantity":10}
                                """.formatted(ITEM_ID, WAREHOUSE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("receive: duplicate Idempotency-Key returns the same response both calls (B5)")
    void receive_duplicateIdempotencyKey_sameResponseBothCalls() throws Exception {
        StockMovementResponse sameResult = movementResponse("KEY-1");
        when(inventoryService.receive(any(StockReceiveRequest.class), eq("KEY-1"))).thenReturn(sameResult);

        String body = """
                {"itemId":"%s","warehouseId":"%s","quantity":10}
                """.formatted(ITEM_ID, WAREHOUSE_ID);

        String firstResponse = mockMvc.perform(post("/v1/inventory/receive")
                        .header("Idempotency-Key", "KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String secondResponse = mockMvc.perform(post("/v1/inventory/receive")
                        .header("Idempotency-Key", "KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(firstResponse).isEqualTo(secondResponse);
        verify(inventoryService, times(2)).receive(any(StockReceiveRequest.class), eq("KEY-1"));
    }

    @Test
    // R7: name said 422 – F5 moved INSUFFICIENT_STOCK to 409 (debt #9). The assertions below always
    // read the status from the enum, so only the name was stale. Renamed in D7; no assertion changed.
    @DisplayName("issue: insufficient stock returns 409 INSUFFICIENT_AVAILABLE_STOCK")
    void issue_insufficientStock_returns409() throws Exception {
        when(inventoryService.issue(any(StockIssueRequest.class), eq(null)))
                .thenThrow(new AppException(BusinessErrorCode.INSUFFICIENT_STOCK));

        mockMvc.perform(post("/v1/inventory/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"%s","warehouseId":"%s","quantity":10}
                                """.formatted(ITEM_ID, WAREHOUSE_ID)))
                .andExpect(status().is(BusinessErrorCode.INSUFFICIENT_STOCK.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.INSUFFICIENT_STOCK.code()));
    }

    @Test
    @DisplayName("balances: returns 200 with paged result")
    void getBalances_returns200PagedResult() throws Exception {
        StockBalanceResponse balance = new StockBalanceResponse(
                UUID.randomUUID(), ITEM_ID, WAREHOUSE_ID, null, null,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, Instant.now());
        Page<StockBalanceResponse> page = new PageImpl<>(List.of(balance));
        when(inventoryService.listBalances(eq(WAREHOUSE_ID), eq(null), any()))
                .thenReturn(PageResult.from(page));

        mockMvc.perform(get("/v1/inventory/balances").param("warehouseId", WAREHOUSE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].itemId").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.result.content[0].qualityHoldQuantity").value(0));
    }
}
