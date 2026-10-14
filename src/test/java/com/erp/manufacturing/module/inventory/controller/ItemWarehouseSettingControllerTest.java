package com.erp.manufacturing.module.inventory.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingRequest;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingResponse;
import com.erp.manufacturing.module.inventory.service.ItemWarehouseSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link ItemWarehouseSettingController} (`D7b`, group B).
 *
 * <p>The upsert endpoint is a {@code PUT} that returns <b>200, not 201</b> (§5.8: PUT is a full
 * replace) even on first write – asserted here because "create returns 201" is the reflex a future
 * change is most likely to apply by mistake.
 */
@WebMvcTest(controllers = ItemWarehouseSettingController.class)
@Import(PlantContextResolver.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ItemWarehouseSettingController – response envelope contract")
class ItemWarehouseSettingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ItemWarehouseSettingService settingService;

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

    private static final UUID SETTING_ID   = UUID.randomUUID();
    private static final UUID ITEM_ID      = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    private ItemWarehouseSettingResponse sampleResponse() {
        return new ItemWarehouseSettingResponse(
                SETTING_ID, ITEM_ID, "MAT-001", "Steel Sheet",
                WAREHOUSE_ID, "WH-01", "Main Warehouse",
                new BigDecimal("25.000000"), new BigDecimal("40.000000"), 7,
                "ACTIVE", Instant.now(), Instant.now());
    }

    private String upsertBody() {
        return """
                {"itemId":"%s","warehouseId":"%s","safetyStock":25,"reorderPoint":40,"leadTimeDays":7}
                """.formatted(ITEM_ID, WAREHOUSE_ID);
    }

    @Test
    @DisplayName("upsert: valid request returns 200 with the stored thresholds")
    void upsert_validRequest_returns200() throws Exception {
        when(settingService.upsert(any(ItemWarehouseSettingRequest.class), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/v1/inventory/item-warehouse-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.settingId").value(SETTING_ID.toString()))
                .andExpect(jsonPath("$.result.safetyStock").value(25.000000))
                .andExpect(jsonPath("$.result.reorderPoint").value(40.000000))
                .andExpect(jsonPath("$.result.leadTimeDays").value(7));
    }

    @Test
    @DisplayName("upsert: missing required threshold returns 400 VALIDATION_ERROR with the field name")
    void upsert_missingLeadTime_returns400WithFieldError() throws Exception {
        mockMvc.perform(put("/v1/inventory/item-warehouse-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"%s","warehouseId":"%s","safetyStock":25,"reorderPoint":40}
                                """.formatted(ITEM_ID, WAREHOUSE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("leadTimeDays"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(settingService);
    }

    @Test
    @DisplayName("upsert: item and warehouse in different companies stays 422 OPERATION_NOT_ALLOWED "
            + "(§5.3 — cross-company master data, not a state conflict)")
    void upsert_itemAndWarehouseInDifferentCompanies_returns422() throws Exception {
        when(settingService.upsert(any(ItemWarehouseSettingRequest.class), any()))
                .thenThrow(new AppException(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                        "Item and warehouse must belong to the same company"));

        mockMvc.perform(put("/v1/inventory/item-warehouse-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertBody()))
                .andExpect(status().is(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(settingService.list(eq(null), eq(WAREHOUSE_ID), eq(null), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/inventory/item-warehouse-settings")
                        .param("warehouseId", WAREHOUSE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].itemCode").value("MAT-001"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("deactivate: returns 200 with a null result payload (§5.8 DELETE pattern)")
    void deactivate_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(delete("/v1/inventory/item-warehouse-settings/" + SETTING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(settingService).deactivate(SETTING_ID, null);
    }

    // ---- G2-3 ----

    @Test
    @DisplayName("list: plantId is forwarded and a matching X-Plant-Id is accepted")
    void list_byPlant_forwardsThePlantAndAcceptsAMatchingHeader() throws Exception {
        UUID plantId = UUID.randomUUID();
        when(settingService.list(eq(plantId), eq(null), eq(null), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/inventory/item-warehouse-settings")
                        .header("X-Plant-Id", plantId.toString())
                        .param("plantId", plantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(settingService).list(eq(plantId), eq(null), eq(null), pageable.capture());
        assertThat(pageable.getValue().getSort()).containsExactly(
                Sort.Order.desc("updatedAt"), Sort.Order.desc("settingId"));
    }

    @Test
    @DisplayName("list: X-Plant-Id naming another plant than plantId is 409 STATE_CONFLICT")
    void list_byPlant_mismatchedHeader_returns409() throws Exception {
        mockMvc.perform(get("/v1/inventory/item-warehouse-settings")
                        .header("X-Plant-Id", UUID.randomUUID().toString())
                        .param("plantId", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(settingService);
    }

    @Test
    @DisplayName("list: a malformed X-Plant-Id is a 400, not a 409")
    void list_byPlant_malformedHeader_returns400() throws Exception {
        mockMvc.perform(get("/v1/inventory/item-warehouse-settings")
                        .header("X-Plant-Id", "not-a-uuid")
                        .param("plantId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.FIELD_FORMAT_INVALID.code()));

        verifyNoInteractions(settingService);
    }

    @Test
    @DisplayName("upsert: replaceDefaults and X-Plant-Id reach the service unchanged")
    void upsert_forwardsReplaceDefaultsAndTheHeader() throws Exception {
        String plantHeader = UUID.randomUUID().toString();
        when(settingService.upsert(any(ItemWarehouseSettingRequest.class), eq(plantHeader))).thenReturn(sampleResponse());

        mockMvc.perform(put("/v1/inventory/item-warehouse-settings")
                        .header("X-Plant-Id", plantHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"%s","warehouseId":"%s","safetyStock":0,"reorderPoint":0,"leadTimeDays":0,
                                 "defaultSupply":true,"replaceDefaults":false}
                                """.formatted(ITEM_ID, WAREHOUSE_ID)))
                .andExpect(status().isOk());

        ArgumentCaptor<ItemWarehouseSettingRequest> captor = ArgumentCaptor.forClass(ItemWarehouseSettingRequest.class);
        verify(settingService).upsert(captor.capture(), eq(plantHeader));
        assertThat(captor.getValue().replaceDefaults()).isFalse();
        assertThat(captor.getValue().defaultSupply()).isTrue();
    }

    @Test
    @DisplayName("upsert: omitted replaceDefaults stays null (legacy behaviour, not false)")
    void upsert_omittedReplaceDefaults_isForwardedAsNull() throws Exception {
        when(settingService.upsert(any(ItemWarehouseSettingRequest.class), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/v1/inventory/item-warehouse-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertBody()))
                .andExpect(status().isOk());

        ArgumentCaptor<ItemWarehouseSettingRequest> captor = ArgumentCaptor.forClass(ItemWarehouseSettingRequest.class);
        verify(settingService).upsert(captor.capture(), eq(null));
        assertThat(captor.getValue().replaceDefaults()).isNull();
    }

    @Test
    @DisplayName("upsert: DEFAULT_CONFLICT surfaces as 409 with its own code")
    void upsert_defaultConflict_returns409() throws Exception {
        when(settingService.upsert(any(ItemWarehouseSettingRequest.class), any()))
                .thenThrow(new AppException(BusinessErrorCode.DEFAULT_CONFLICT, "Default role already held"));

        mockMvc.perform(put("/v1/inventory/item-warehouse-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.DEFAULT_CONFLICT.code()));
    }

    @Test
    @DisplayName("deactivate: X-Plant-Id is forwarded to the service")
    void deactivate_forwardsTheHeader() throws Exception {
        String plantHeader = UUID.randomUUID().toString();

        mockMvc.perform(delete("/v1/inventory/item-warehouse-settings/" + SETTING_ID)
                        .header("X-Plant-Id", plantHeader))
                .andExpect(status().isOk());

        verify(settingService).deactivate(SETTING_ID, plantHeader);
    }
}
