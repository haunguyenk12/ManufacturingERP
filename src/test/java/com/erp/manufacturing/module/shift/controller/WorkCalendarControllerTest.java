package com.erp.manufacturing.module.shift.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.shift.dto.WorkCalendarCreateRequest;
import com.erp.manufacturing.module.shift.dto.WorkCalendarResponse;
import com.erp.manufacturing.module.shift.service.WorkCalendarService;
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
 * Contract test for {@link WorkCalendarController} (C2-7) — envelope contract only;
 * {@code @PreAuthorize} itself is covered by {@code WorkCalendarMethodSecurityTest} (rule R2).
 */
@WebMvcTest(controllers = WorkCalendarController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("WorkCalendarController – response envelope contract")
class WorkCalendarControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    WorkCalendarService workCalendarService;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID CALENDAR_ID = UUID.randomUUID();

    private WorkCalendarResponse sampleResponse(String status) {
        return new WorkCalendarResponse(CALENDAR_ID, PLANT_ID, "CAL-01", "Default Calendar",
                LocalDate.of(2026, 1, 1), null, status, List.of(), List.of(), 0L, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("create: valid request returns 201 with the ACTIVE work calendar")
    void create_validRequest_returns201Created() throws Exception {
        when(workCalendarService.create(eq(PLANT_ID), any(WorkCalendarCreateRequest.class)))
                .thenReturn(sampleResponse("ACTIVE"));

        mockMvc.perform(post("/v1/plants/" + PLANT_ID + "/work-calendars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"cal-01","name":"Default Calendar","effectiveFrom":"2026-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.workCalendarId").value(CALENDAR_ID.toString()))
                .andExpect(jsonPath("$.result.plantId").value(PLANT_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.version").value(0));
    }

    @Test
    @DisplayName("create: blank name returns 400 VALIDATION_ERROR with the field name")
    void create_blankName_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/v1/plants/" + PLANT_ID + "/work-calendars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CAL-01","name":"","effectiveFrom":"2026-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("create: duplicate code returns 409 RESOURCE_ALREADY_EXISTS")
    void create_duplicateCode_returns409AlreadyExists() throws Exception {
        when(workCalendarService.create(eq(PLANT_ID), any(WorkCalendarCreateRequest.class)))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Work calendar code already exists: CAL-01"));

        mockMvc.perform(post("/v1/plants/" + PLANT_ID + "/work-calendars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CAL-01","name":"Default Calendar","effectiveFrom":"2026-01-01"}
                                """))
                .andExpect(status().is(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("get: unknown id returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404EntityNotFound() throws Exception {
        when(workCalendarService.get(CALENDAR_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND,
                        "Work calendar not found with id: " + CALENDAR_ID));

        mockMvc.perform(get("/v1/work-calendars/" + CALENDAR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("update: request body has no code/plantId field to send")
    void update_validRequest_returns200() throws Exception {
        when(workCalendarService.update(eq(CALENDAR_ID), any())).thenReturn(sampleResponse("ACTIVE"));

        mockMvc.perform(patch("/v1/work-calendars/" + CALENDAR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Default Calendar (updated)"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("activate: returns 200 with ACTIVE status")
    void activate_returns200Active() throws Exception {
        when(workCalendarService.activate(CALENDAR_ID)).thenReturn(sampleResponse("ACTIVE"));

        mockMvc.perform(post("/v1/work-calendars/" + CALENDAR_ID + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("deactivate: returns 200 with INACTIVE status")
    void deactivate_returns200Inactive() throws Exception {
        when(workCalendarService.deactivate(CALENDAR_ID)).thenReturn(sampleResponse("INACTIVE"));

        mockMvc.perform(post("/v1/work-calendars/" + CALENDAR_ID + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("delete: is the same command as POST .../deactivate — 200 with null result")
    void delete_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(delete("/v1/work-calendars/" + CALENDAR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(workCalendarService).deactivate(CALENDAR_ID);
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(workCalendarService.list(eq(PLANT_ID), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse("ACTIVE")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/plants/" + PLANT_ID + "/work-calendars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].code").value("CAL-01"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        verify(workCalendarService).list(eq(PLANT_ID), isNull(), any());
    }
}
