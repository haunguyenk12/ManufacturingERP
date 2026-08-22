package com.erp.manufacturing.module.uom.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.uom.dto.UomCreateRequest;
import com.erp.manufacturing.module.uom.dto.UomResponse;
import com.erp.manufacturing.module.uom.service.UomService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link UomController} (C2-3) — envelope contract only; {@code @PreAuthorize}
 * itself is covered by {@code UomMethodSecurityTest} (rule R2 separation).
 */
@WebMvcTest(controllers = UomController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UomController – response envelope contract")
class UomControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UomService uomService;

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

    private static final UUID UOM_ID = UUID.randomUUID();

    private UomResponse sampleUom(String status) {
        return new UomResponse(UOM_ID, "KG", "Kilogram", "Base mass unit", status,
                Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("create: valid request returns 201 with the ACTIVE uom")
    void create_validRequest_returns201Created() throws Exception {
        when(uomService.create(any(UomCreateRequest.class))).thenReturn(sampleUom("ACTIVE"));

        mockMvc.perform(post("/v1/uoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"kg","name":"Kilogram","description":"Base mass unit"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.uomId").value(UOM_ID.toString()))
                .andExpect(jsonPath("$.result.code").value("KG"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("create: blank name returns 400 VALIDATION_ERROR with the field name")
    void create_blankName_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/v1/uoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"KG","name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("create: duplicate code returns 409 RESOURCE_ALREADY_EXISTS")
    void create_duplicateCode_returns409AlreadyExists() throws Exception {
        when(uomService.create(any(UomCreateRequest.class)))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                        "UOM code already exists: KG"));

        mockMvc.perform(post("/v1/uoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"KG","name":"Kilogram"}
                                """))
                .andExpect(status().is(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("get: unknown id returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404EntityNotFound() throws Exception {
        when(uomService.get(UOM_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND, "UOM not found with id: " + UOM_ID));

        mockMvc.perform(get("/v1/uoms/" + UOM_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("update: request body has no code field to send — server ignores code entirely")
    void update_validRequest_returns200() throws Exception {
        when(uomService.update(eq(UOM_ID), any())).thenReturn(sampleUom("ACTIVE"));

        mockMvc.perform(patch("/v1/uoms/" + UOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Kilogram (updated)"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("activate: returns 200 with ACTIVE status")
    void activate_returns200Active() throws Exception {
        when(uomService.activate(UOM_ID)).thenReturn(sampleUom("ACTIVE"));

        mockMvc.perform(post("/v1/uoms/" + UOM_ID + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("deactivate: returns 200 with INACTIVE status")
    void deactivate_returns200Inactive() throws Exception {
        when(uomService.deactivate(UOM_ID)).thenReturn(sampleUom("INACTIVE"));

        mockMvc.perform(post("/v1/uoms/" + UOM_ID + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(uomService.list(isNull(), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(sampleUom("ACTIVE")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/uoms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].code").value("KG"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        verify(uomService).list(isNull(), isNull(), any());
    }

    @Test
    @DisplayName("list: size above max is clamped to 100 (A4)")
    void list_sizeAboveMax_isClampedToHundred() throws Exception {
        when(uomService.list(isNull(), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0L, 0, true, true));

        mockMvc.perform(get("/v1/uoms").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.size").value(100));
    }
}
