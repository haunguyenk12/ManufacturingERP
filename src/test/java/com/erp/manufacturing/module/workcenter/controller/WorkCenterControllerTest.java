package com.erp.manufacturing.module.workcenter.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterCreateRequest;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterResponse;
import com.erp.manufacturing.module.workcenter.service.WorkCenterService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link WorkCenterController} (C2-6) — envelope contract only;
 * {@code @PreAuthorize} itself is covered by {@code WorkCenterMethodSecurityTest} (rule R2).
 */
@WebMvcTest(controllers = WorkCenterController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("WorkCenterController – response envelope contract")
class WorkCenterControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    WorkCenterService workCenterService;

    // Unused directly by these tests – required only so the auto-detected security filters
    // can be constructed by the @WebMvcTest slice, even with addFilters = false.
    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID WORK_CENTER_ID = UUID.randomUUID();

    private WorkCenterResponse sampleResponse(String status) {
        return new WorkCenterResponse(WORK_CENTER_ID, PLANT_ID, "WC-01", "Line 1", "Main line",
                "LINE", 2, status, 0L, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("create: valid request returns 201 with the ACTIVE work center")
    void create_validRequest_returns201Created() throws Exception {
        when(workCenterService.create(eq(PLANT_ID), any(WorkCenterCreateRequest.class)))
                .thenReturn(sampleResponse("ACTIVE"));

        mockMvc.perform(post("/api/v1/plants/" + PLANT_ID + "/work-centers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"wc-01","name":"Line 1","description":"Main line",
                                 "capacityUnitType":"LINE","capacityUnits":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.workCenterId").value(WORK_CENTER_ID.toString()))
                .andExpect(jsonPath("$.result.plantId").value(PLANT_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.version").value(0));
    }

    @Test
    @DisplayName("create: blank name returns 400 VALIDATION_ERROR with the field name")
    void create_blankName_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/plants/" + PLANT_ID + "/work-centers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WC-01","name":"","capacityUnitType":"LINE","capacityUnits":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("create: duplicate code returns 409 RESOURCE_ALREADY_EXISTS")
    void create_duplicateCode_returns409AlreadyExists() throws Exception {
        when(workCenterService.create(eq(PLANT_ID), any(WorkCenterCreateRequest.class)))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Work center code already exists: WC-01"));

        mockMvc.perform(post("/api/v1/plants/" + PLANT_ID + "/work-centers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WC-01","name":"Line 1","capacityUnitType":"LINE","capacityUnits":2}
                                """))
                .andExpect(status().is(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("get: unknown id returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404EntityNotFound() throws Exception {
        when(workCenterService.get(WORK_CENTER_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND,
                        "Work center not found with id: " + WORK_CENTER_ID));

        mockMvc.perform(get("/api/v1/work-centers/" + WORK_CENTER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("update: request body has no code/plantId field to send")
    void update_validRequest_returns200() throws Exception {
        when(workCenterService.update(eq(WORK_CENTER_ID), any())).thenReturn(sampleResponse("ACTIVE"));

        mockMvc.perform(patch("/api/v1/work-centers/" + WORK_CENTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Line 1 (updated)"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("activate: returns 200 with ACTIVE status")
    void activate_returns200Active() throws Exception {
        when(workCenterService.activate(WORK_CENTER_ID)).thenReturn(sampleResponse("ACTIVE"));

        mockMvc.perform(post("/api/v1/work-centers/" + WORK_CENTER_ID + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("deactivate: returns 200 with INACTIVE status")
    void deactivate_returns200Inactive() throws Exception {
        when(workCenterService.deactivate(WORK_CENTER_ID)).thenReturn(sampleResponse("INACTIVE"));

        mockMvc.perform(post("/api/v1/work-centers/" + WORK_CENTER_ID + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("delete: is the same command as POST .../deactivate — 200 with null result")
    void delete_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/work-centers/" + WORK_CENTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(workCenterService).deactivate(WORK_CENTER_ID);
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(workCenterService.list(eq(PLANT_ID), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse("ACTIVE")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/plants/" + PLANT_ID + "/work-centers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].code").value("WC-01"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        verify(workCenterService).list(eq(PLANT_ID), isNull(), any());
    }

    @Test
    @DisplayName("list: size above max is clamped to 100 (A4)")
    void list_sizeAboveMax_isClampedToHundred() throws Exception {
        when(workCenterService.list(eq(PLANT_ID), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0L, 0, true, true));

        mockMvc.perform(get("/api/v1/plants/" + PLANT_ID + "/work-centers").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.size").value(100));
    }
}
