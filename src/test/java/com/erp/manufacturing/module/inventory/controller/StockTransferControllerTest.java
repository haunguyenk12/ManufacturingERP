package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.inventory.dto.StockTransferLineResponse;
import com.erp.manufacturing.module.inventory.dto.StockTransferRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferResponse;
import com.erp.manufacturing.module.inventory.service.StockTransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StockTransferController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("StockTransferController – contract (G4-2)")
class StockTransferControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean StockTransferService transferService;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID FROM = UUID.randomUUID();
    private static final UUID TO = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();
    private static final UUID PLANT = UUID.randomUUID();

    private StockTransferResponse response() {
        return new StockTransferResponse(UUID.randomUUID(), "TR-ABCD1234", "POSTED", PLANT,
                FROM, "WH-A", "Kho A", TO, "WH-B", "Kho B", "Move", Instant.parse("2026-10-15T00:00:00Z"),
                "trace-1", UUID.randomUUID(), "kho.a", 0L,
                List.of(new StockTransferLineResponse(UUID.randomUUID(), 1, ITEM, "RM-1", "Steel", "KG",
                        null, null, null, new BigDecimal("2"), UUID.randomUUID(), UUID.randomUUID())));
    }

    private String body(String lines) {
        return """
                {"fromWarehouseId":"%s","toWarehouseId":"%s","reason":"Move","lines":%s}
                """.formatted(FROM, TO, lines);
    }

    @Test
    @DisplayName("POST: 201 with the posted document; key and X-Plant-Id reach the service unchanged")
    void create_returns201AndForwardsHeaders() throws Exception {
        when(transferService.create(any(StockTransferRequest.class), eq("TR-1"), eq(PLANT.toString())))
                .thenReturn(response());

        mockMvc.perform(post("/v1/inventory/transfers")
                        .header("Idempotency-Key", "TR-1")
                        .header("X-Plant-Id", PLANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"itemId\":\"%s\",\"quantity\":2}]".formatted(ITEM))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.status").value("POSTED"))
                .andExpect(jsonPath("$.result.code").value("TR-ABCD1234"))
                .andExpect(jsonPath("$.result.lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.result.lines[0].outMovementId").isString())
                .andExpect(jsonPath("$.result.lines[0].inMovementId").isString());
    }

    @Test
    @DisplayName("POST: no lines is 400 on lines")
    void create_withoutLines_returns400() throws Exception {
        mockMvc.perform(post("/v1/inventory/transfers")
                        .header("Idempotency-Key", "TR-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines"));

        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("POST: 101 lines is 400 — the document is capped at 100 lines")
    void create_withMoreThanHundredLines_returns400() throws Exception {
        String lines = IntStream.range(0, 101)
                .mapToObj(i -> "{\"itemId\":\"%s\",\"quantity\":1}".formatted(UUID.randomUUID()))
                .collect(Collectors.joining(",", "[", "]"));

        mockMvc.perform(post("/v1/inventory/transfers")
                        .header("Idempotency-Key", "TR-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lines)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines"));

        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("POST: a 7th decimal on a line is 400 naming that line — R08")
    void create_quantityWithSevenDecimals_returns400OnTheLine() throws Exception {
        mockMvc.perform(post("/v1/inventory/transfers")
                        .header("Idempotency-Key", "TR-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"itemId\":\"%s\",\"quantity\":0.0000001}]".formatted(ITEM))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].quantity"));

        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("POST: insufficient free stock is 409 with the failing line as field")
    void create_insufficientStock_returns409NamingTheLine() throws Exception {
        when(transferService.create(any(StockTransferRequest.class), eq("TR-1"), eq(null)))
                .thenThrow(ExceptionFactory.withErrors(BusinessErrorCode.INSUFFICIENT_STOCK,
                        Map.of("lines[1].quantity", "Requested quantity exceeds free available stock")));

        mockMvc.perform(post("/v1/inventory/transfers")
                        .header("Idempotency-Key", "TR-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"itemId\":\"%s\",\"quantity\":2}]".formatted(ITEM))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.INSUFFICIENT_STOCK.code()))
                .andExpect(jsonPath("$.errors[0].field").value("lines[1].quantity"));
    }

    @Test
    @DisplayName("POST: same warehouse is 400 VALIDATION_ERROR on toWarehouseId")
    void create_sameWarehouse_returns400() throws Exception {
        when(transferService.create(any(StockTransferRequest.class), eq("TR-1"), eq(null)))
                .thenThrow(ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                        Map.of("toWarehouseId", "must be different from fromWarehouseId")));

        mockMvc.perform(post("/v1/inventory/transfers")
                        .header("Idempotency-Key", "TR-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"itemId\":\"%s\",\"quantity\":2}]".formatted(ITEM))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("toWarehouseId"));
    }

    @Test
    @DisplayName("GET list: plantId is required and the page envelope is complete")
    void list_returnsPageAndRequiresPlant() throws Exception {
        when(transferService.list(eq(PLANT), eq(null), eq("TR-"), eq(null), any(Pageable.class)))
                .thenReturn(PageResult.from(Page.empty()));

        mockMvc.perform(get("/v1/inventory/transfers").param("plantId", PLANT.toString()).param("search", "TR-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.totalElements").value(0));
        mockMvc.perform(get("/v1/inventory/transfers"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("plantId"));
    }

    @Test
    @DisplayName("GET detail: 200 with lines")
    void get_returns200() throws Exception {
        StockTransferResponse response = response();
        when(transferService.get(response.transferId())).thenReturn(response);

        mockMvc.perform(get("/v1/inventory/transfers/{id}", response.transferId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.transferId").value(response.transferId().toString()))
                .andExpect(jsonPath("$.result.lines.length()").value(1));
    }
}
