package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.dto.InventoryReservationResponse;
import com.erp.manufacturing.module.workorder.service.query.InventoryReservationQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of {@link InventoryReservationController} (G4-4); the query service is mocked. */
@WebMvcTest(controllers = InventoryReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryReservationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean InventoryReservationQueryService reservationQueryService;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();

    private static InventoryReservationResponse reservation() {
        return new InventoryReservationResponse(RESERVATION_ID, UUID.randomUUID(), "WO-0001", "RELEASED",
                UUID.randomUUID(), 10, ITEM_ID, "RM-001", "Steel tube", "EA", WAREHOUSE_ID, null, null,
                new BigDecimal("12.500000"), new BigDecimal("2.500000"), new BigDecimal("10.000000"),
                "ACTIVE", Instant.parse("2026-11-01T00:00:00Z"), "WORK_ORDER", null, null, null, null);
    }

    @Test
    void list_returnsThePageEnvelopeWithQuantitiesAndOwner() throws Exception {
        when(reservationQueryService.list(eq(WAREHOUSE_ID), eq(ITEM_ID), isNull(),
                eq(MaterialReservationStatus.ACTIVE), any()))
                .thenReturn(new PageResult<>(List.of(reservation()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/v1/inventory/reservations")
                        .param("warehouseId", WAREHOUSE_ID.toString())
                        .param("itemId", ITEM_ID.toString())
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].reservationId").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.result.content[0].remainingQuantity").value(10.0))
                .andExpect(jsonPath("$.result.content[0].ownerType").value("WORK_ORDER"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    void list_defaultsToNewestFirstWithAStableTieBreakerAndCapsThePageSize() throws Exception {
        when(reservationQueryService.list(any(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0, 0, true, true));

        mockMvc.perform(get("/v1/inventory/reservations")
                        .param("warehouseId", WAREHOUSE_ID.toString())
                        .param("size", "5000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationQueryService).list(eq(WAREHOUSE_ID), isNull(), isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "reservationId")));
    }

    @Test
    void list_withoutWarehouseId_returns400NamingTheParameter() throws Exception {
        mockMvc.perform(get("/v1/inventory/reservations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("warehouseId"));

        verifyNoInteractions(reservationQueryService);
    }

    @Test
    void list_unknownStatus_returns400() throws Exception {
        mockMvc.perform(get("/v1/inventory/reservations")
                        .param("warehouseId", WAREHOUSE_ID.toString())
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(reservationQueryService);
    }
}
