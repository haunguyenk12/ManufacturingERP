package com.erp.manufacturing.module.costing.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.costing.dto.ItemStandardCostRequest;
import com.erp.manufacturing.module.costing.dto.ItemStandardCostResponse;
import com.erp.manufacturing.module.costing.service.ItemStandardCostService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link ItemStandardCostController} (P3) — envelope contract only;
 * {@code @PreAuthorize} itself is covered by {@code ItemStandardCostMethodSecurityTest} (rule R2).
 */
@WebMvcTest(controllers = ItemStandardCostController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ItemStandardCostController – response envelope contract")
class ItemStandardCostControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ItemStandardCostService itemStandardCostService;

    // Unused directly by these tests – required only so the auto-detected security filters
    // can be constructed by the @WebMvcTest slice, even with addFilters = false.
    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID COST_ID = UUID.randomUUID();

    private ItemStandardCostResponse sampleResponse() {
        return new ItemStandardCostResponse(COST_ID, COMPANY_ID, ITEM_ID, "RM-001",
                new BigDecimal("5"), new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("8"),
                0L, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("upsert: valid request returns 200 with the computed totalStandardCost")
    void upsert_validRequest_returns200() throws Exception {
        when(itemStandardCostService.upsert(eq(COMPANY_ID), eq(ITEM_ID), any(ItemStandardCostRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/companies/" + COMPANY_ID + "/items/" + ITEM_ID + "/standard-cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialCost":5,"laborCost":2,"overheadCost":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.itemId").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.result.totalStandardCost").value(8));
    }

    @Test
    @DisplayName("upsert: negative materialCost returns 400 VALIDATION_ERROR with the field name")
    void upsert_negativeMaterialCost_returns400WithFieldError() throws Exception {
        mockMvc.perform(put("/api/v1/companies/" + COMPANY_ID + "/items/" + ITEM_ID + "/standard-cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialCost":-1,"laborCost":2,"overheadCost":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("materialCost"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("get: unknown item standard cost returns 404 ENTITY_NOT_FOUND")
    void get_unknownCost_returns404EntityNotFound() throws Exception {
        when(itemStandardCostService.get(COMPANY_ID, ITEM_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND,
                        "Item standard cost not found for item: " + ITEM_ID));

        mockMvc.perform(get("/api/v1/companies/" + COMPANY_ID + "/items/" + ITEM_ID + "/standard-cost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(itemStandardCostService.list(eq(COMPANY_ID), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/companies/" + COMPANY_ID + "/items/standard-costs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].itemCode").value("RM-001"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("list: size above max is clamped to 100 (A4)")
    void list_sizeAboveMax_isClampedToHundred() throws Exception {
        when(itemStandardCostService.list(eq(COMPANY_ID), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0L, 0, true, true));

        mockMvc.perform(get("/api/v1/companies/" + COMPANY_ID + "/items/standard-costs").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.size").value(100));
    }
}
