package com.erp.manufacturing.module.shift.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.shift.dto.ShiftCreateRequest;
import com.erp.manufacturing.module.shift.dto.ShiftResponse;
import com.erp.manufacturing.module.shift.service.ShiftService;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link ShiftController} (C2-7) — envelope contract only; {@code @PreAuthorize}
 * itself is covered by {@code ShiftMethodSecurityTest} (rule R2).
 */
@WebMvcTest(controllers = ShiftController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ShiftController – response envelope contract")
class ShiftControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ShiftService shiftService;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID SHIFT_ID = UUID.randomUUID();

    private ShiftResponse sampleResponse(String status) {
        return new ShiftResponse(SHIFT_ID, PLANT_ID, "SH-01", "Day Shift",
                LocalTime.of(8, 0), LocalTime.of(17, 0), status, List.of(), 0L, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("create: valid request returns 201 with the ACTIVE shift")
    void create_validRequest_returns201Created() throws Exception {
        when(shiftService.create(eq(PLANT_ID), any(ShiftCreateRequest.class)))
                .thenReturn(sampleResponse("ACTIVE"));

        mockMvc.perform(post("/api/v1/plants/" + PLANT_ID + "/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"sh-01","name":"Day Shift","startTime":"08:00:00","endTime":"17:00:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.shiftId").value(SHIFT_ID.toString()))
                .andExpect(jsonPath("$.result.plantId").value(PLANT_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.version").value(0));
    }

    @Test
    @DisplayName("create: blank name returns 400 VALIDATION_ERROR with the field name")
    void create_blankName_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/plants/" + PLANT_ID + "/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"SH-01","name":"","startTime":"08:00:00","endTime":"17:00:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("create: duplicate code returns 409 RESOURCE_ALREADY_EXISTS")
    void create_duplicateCode_returns409AlreadyExists() throws Exception {
        when(shiftService.create(eq(PLANT_ID), any(ShiftCreateRequest.class)))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Shift code already exists: SH-01"));

        mockMvc.perform(post("/api/v1/plants/" + PLANT_ID + "/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"SH-01","name":"Day Shift","startTime":"08:00:00","endTime":"17:00:00"}
                                """))
                .andExpect(status().is(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("get: unknown id returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404EntityNotFound() throws Exception {
        when(shiftService.get(SHIFT_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND,
                        "Shift not found with id: " + SHIFT_ID));

        mockMvc.perform(get("/api/v1/shifts/" + SHIFT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("update: request body has no code/plantId field to send")
    void update_validRequest_returns200() throws Exception {
        when(shiftService.update(eq(SHIFT_ID), any())).thenReturn(sampleResponse("ACTIVE"));

        mockMvc.perform(patch("/api/v1/shifts/" + SHIFT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Day Shift (updated)"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("activate: returns 200 with ACTIVE status")
    void activate_returns200Active() throws Exception {
        when(shiftService.activate(SHIFT_ID)).thenReturn(sampleResponse("ACTIVE"));

        mockMvc.perform(post("/api/v1/shifts/" + SHIFT_ID + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("deactivate: returns 200 with INACTIVE status")
    void deactivate_returns200Inactive() throws Exception {
        when(shiftService.deactivate(SHIFT_ID)).thenReturn(sampleResponse("INACTIVE"));

        mockMvc.perform(post("/api/v1/shifts/" + SHIFT_ID + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("delete: is the same command as POST .../deactivate — 200 with null result")
    void delete_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/shifts/" + SHIFT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(shiftService).deactivate(SHIFT_ID);
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(shiftService.list(eq(PLANT_ID), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse("ACTIVE")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/plants/" + PLANT_ID + "/shifts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].code").value("SH-01"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        verify(shiftService).list(eq(PLANT_ID), isNull(), any());
    }
}
