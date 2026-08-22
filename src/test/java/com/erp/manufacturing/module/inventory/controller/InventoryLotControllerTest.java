package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.inventory.dto.InventoryLotBalanceResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryLotDetailResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryLotResponse;
import com.erp.manufacturing.module.inventory.service.InventoryLotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link InventoryLotController} (C2-2) — envelope contract only;
 * {@code @PreAuthorize} itself is covered by {@code InventoryLotMethodSecurityTest} (rule R2).
 */
@WebMvcTest(controllers = InventoryLotController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InventoryLotController – response envelope contract")
class InventoryLotControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    InventoryLotService lotService;

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

    private final UUID warehouseId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();

    @Test
    @DisplayName("list: default request returns 200 with paginated envelope")
    void list_defaultRequest_returns200() throws Exception {
        InventoryLotResponse response = new InventoryLotResponse(
                lotId, UUID.randomUUID(), "RM-001", "Steel Coil", warehouseId, "WH1", "Warehouse 1",
                "LOT-1", "AVAILABLE", new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("10"),
                Instant.parse("2026-07-15T06:30:00Z"), Instant.parse("2026-07-15T06:30:00Z"),
                null, "RECEIVE", "WORK_ORDER", "wo-1",
                Instant.parse("2026-08-01T00:00:00Z"), 0L,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"));
        when(lotService.list(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.from(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/v1/inventory/lots").param("warehouseId", warehouseId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].lotId").value(lotId.toString()))
                .andExpect(jsonPath("$.result.content[0].status").value("AVAILABLE"))
                // FE labels "date received" with receivedAt. It must be the lot's own stamp, not the
                // row audit timestamp, so the fixture keeps the two deliberately different.
                .andExpect(jsonPath("$.result.content[0].receivedAt").value("2026-07-15T06:30:00Z"))
                .andExpect(jsonPath("$.result.content[0].createdAt").value("2026-08-01T00:00:00Z"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("list: bad status query param returns 400 VALIDATION_ERROR")
    void list_invalidStatusParam_returns400() throws Exception {
        mockMvc.perform(get("/v1/inventory/lots")
                        .param("warehouseId", warehouseId.toString())
                        .param("status", "NOT_A_REAL_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
    }

    @Test
    @DisplayName("get: known id returns 200 with the balances[] breakdown")
    void get_knownId_returns200WithBalances() throws Exception {
        InventoryLotDetailResponse response = new InventoryLotDetailResponse(
                lotId, UUID.randomUUID(), "RM-001", "Steel Coil", "LOT-1", "AVAILABLE",
                Instant.parse("2026-07-15T06:30:00Z"), Instant.parse("2026-07-15T06:30:00Z"),
                null, "RECEIVE", "WORK_ORDER", "wo-1",
                Instant.parse("2026-08-01T00:00:00Z"), 0L,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                List.of(new InventoryLotBalanceResponse(
                        warehouseId, "WH1", "Warehouse 1", new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("10"))));
        when(lotService.get(lotId, warehouseId)).thenReturn(response);

        mockMvc.perform(get("/v1/inventory/lots/" + lotId)
                        .param("warehouseId", warehouseId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.lotId").value(lotId.toString()))
                .andExpect(jsonPath("$.result.receivedAt").value("2026-07-15T06:30:00Z"))
                .andExpect(jsonPath("$.result.createdAt").value("2026-08-01T00:00:00Z"))
                .andExpect(jsonPath("$.result.balances").isArray())
                .andExpect(jsonPath("$.result.balances[0].warehouseId").value(warehouseId.toString()));
    }

    @Test
    @DisplayName("get: unknown id returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404() throws Exception {
        when(lotService.get(lotId, null)).thenThrow(new AppException(
                ValidationErrorCode.RESOURCE_NOT_FOUND, "Lot not found: " + lotId));

        mockMvc.perform(get("/v1/inventory/lots/" + lotId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("get: invalid warehouseId query param returns 400 VALIDATION_ERROR")
    void get_invalidWarehouseId_returns400() throws Exception {
        mockMvc.perform(get("/v1/inventory/lots/" + lotId)
                        .param("warehouseId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
    }

    @Test
    @DisplayName("OpenAPI: lot detail publishes the optional warehouseId query parameter")
    void openApi_lotDetailPublishesWarehouseIdQueryParameter() throws Exception {
        var method = InventoryLotController.class.getDeclaredMethod("get", UUID.class, UUID.class);
        var annotation = method.getParameters()[1]
                .getAnnotation(io.swagger.v3.oas.annotations.Parameter.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("warehouseId");
        assertThat(annotation.in()).isEqualTo(io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY);
        assertThat(annotation.required()).isFalse();
    }

    @Test
    @DisplayName("changeStatus: bad newStatus enum value returns 400 VALIDATION_ERROR")
    void changeStatus_invalidNewStatus_returns400() throws Exception {
        mockMvc.perform(post("/v1/inventory/lots/" + lotId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":\"" + warehouseId + "\",\"newStatus\":\"NOT_A_REAL_STATUS\",\"reason\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
    }

    @Test
    @DisplayName("changeStatus: valid request returns 200 with the updated lot")
    void changeStatus_validRequest_returns200() throws Exception {
        InventoryLotResponse response = new InventoryLotResponse(
                lotId, UUID.randomUUID(), "RM-001", "Steel Coil", warehouseId, "WH1", "Warehouse 1",
                "LOT-1", "HOLD", new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("10"),
                Instant.parse("2026-07-15T06:30:00Z"), Instant.parse("2026-07-15T06:30:00Z"),
                null, "RECEIVE", "WORK_ORDER", "wo-1",
                Instant.parse("2026-08-01T00:00:00Z"), 1L,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-06T00:00:00Z"));
        when(lotService.changeStatus(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/v1/inventory/lots/" + lotId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":\"" + warehouseId + "\",\"newStatus\":\"HOLD\",\"reason\":\"Found damage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.status").value("HOLD"));
    }

    @Test
    @DisplayName("changeStatus: blank reason returns 400 VALIDATION_ERROR")
    void changeStatus_blankReason_returns400() throws Exception {
        mockMvc.perform(post("/v1/inventory/lots/" + lotId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":\"" + warehouseId + "\",\"newStatus\":\"HOLD\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
    }
}
