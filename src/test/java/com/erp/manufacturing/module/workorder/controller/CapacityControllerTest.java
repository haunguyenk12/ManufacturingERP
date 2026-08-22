package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityBoardLineResponse;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentResponse;
import com.erp.manufacturing.module.workorder.service.ScheduleAdjustmentService;
import com.erp.manufacturing.module.workorder.service.query.CapacityBoardService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link CapacityController} (C2-8) — envelope contract only; {@code @PreAuthorize}
 * itself is covered by {@code CapacityBoardServiceMethodSecurityTest}/
 * {@code ScheduleAdjustmentServiceMethodSecurityTest} (rule R2).
 */
@WebMvcTest(controllers = CapacityController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CapacityController – response envelope contract")
class CapacityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean CapacityBoardService capacityBoardService;
    @MockBean ScheduleAdjustmentService scheduleAdjustmentService;

    // Unused directly by these tests – required only so the auto-detected security filters
    // can be constructed by the @WebMvcTest slice, even with addFilters = false.
    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID WORK_ORDER_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final UUID WORK_CENTER_ID = UUID.randomUUID();

    private CapacityBoardLineResponse sampleLine() {
        Instant start = Instant.parse("2026-01-05T08:00:00Z");
        return new CapacityBoardLineResponse(
                OPERATION_ID, 1, "Assembly", WORK_ORDER_ID, "WO-001", "RELEASED",
                WORK_CENTER_ID, "WC-01", "Line 1", PLANT_ID, "P1",
                start, start.plusSeconds(3600),
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("60"),
                new BigDecimal("480"), new BigDecimal("120"), new BigDecimal("25.00"),
                false, false, 0L);
    }

    @Test
    @DisplayName("getBoard: valid request returns 200 with the full PageResult envelope")
    void getBoard_validRequest_returns200WithFullPageEnvelope() throws Exception {
        when(capacityBoardService.getBoard(eq(PLANT_ID), eq(LocalDate.of(2026, 1, 5)),
                eq(LocalDate.of(2026, 1, 5)), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(sampleLine()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/plants/" + PLANT_ID + "/capacity-board")
                        .param("from", "2026-01-05").param("to", "2026-01-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].workOrderOperationId").value(OPERATION_ID.toString()))
                .andExpect(jsonPath("$.result.content[0].overload").value(false))
                .andExpect(jsonPath("$.result.content[0].utilizationPercent").value(25.00))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("getBoard: from after to returns 400 VALIDATION_ERROR")
    void getBoard_fromAfterTo_returns400() throws Exception {
        when(capacityBoardService.getBoard(eq(PLANT_ID), eq(LocalDate.of(2026, 1, 5)),
                eq(LocalDate.of(2026, 1, 1)), any(), any(), any()))
                .thenThrow(new AppException(ValidationErrorCode.INVALID_INPUT, "'from' must not be after 'to'"));

        mockMvc.perform(get("/v1/plants/" + PLANT_ID + "/capacity-board")
                        .param("from", "2026-01-05").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("getBoard: missing required 'from'/'to' returns 400 naming the parameter")
    void getBoard_missingRequiredParam_returns400() throws Exception {
        mockMvc.perform(get("/v1/plants/" + PLANT_ID + "/capacity-board").param("to", "2026-01-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @Test
    @DisplayName("adjustSchedule: valid request returns 200 with the advisory conflict flags")
    void adjustSchedule_validRequest_returns200WithConflictFlags() throws Exception {
        Instant start = Instant.parse("2026-01-05T08:00:00Z");
        ScheduleAdjustmentResponse response = new ScheduleAdjustmentResponse(
                OPERATION_ID, start, start.plusSeconds(1800), "Delay due to material shortage", 1L,
                true, false, false, new BigDecimal("480"), new BigDecimal("120"), new BigDecimal("25.00"));
        when(scheduleAdjustmentService.adjust(eq(WORK_ORDER_ID), eq(OPERATION_ID), any()))
                .thenReturn(response);

        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/operations/" + OPERATION_ID
                        + "/schedule-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plannedStartAt":"2026-01-05T08:00:00Z","plannedEndAt":"2026-01-05T08:30:00Z",
                                 "reason":"Delay due to material shortage","expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.sequenceConflict").value(true))
                .andExpect(jsonPath("$.result.calendarConflict").value(false))
                .andExpect(jsonPath("$.result.capacityOverload").value(false))
                .andExpect(jsonPath("$.result.version").value(1));
    }

    @Test
    @DisplayName("adjustSchedule: stale expectedVersion returns 409 CONCURRENT_MODIFICATION")
    void adjustSchedule_staleVersion_returns409() throws Exception {
        when(scheduleAdjustmentService.adjust(eq(WORK_ORDER_ID), eq(OPERATION_ID), any()))
                .thenThrow(new AppException(BusinessErrorCode.CONCURRENT_MODIFICATION,
                        "Work order operation was modified by another request"));

        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/operations/" + OPERATION_ID
                        + "/schedule-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plannedStartAt":"2026-01-05T08:00:00Z","plannedEndAt":"2026-01-05T08:30:00Z",
                                 "reason":"Delay","expectedVersion":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.CONCURRENT_MODIFICATION.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("adjustSchedule: blank reason returns 400 VALIDATION_ERROR naming the field")
    void adjustSchedule_blankReason_returns400() throws Exception {
        mockMvc.perform(post("/v1/work-orders/" + WORK_ORDER_ID + "/operations/" + OPERATION_ID
                        + "/schedule-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plannedStartAt":"2026-01-05T08:00:00Z","plannedEndAt":"2026-01-05T08:30:00Z",
                                 "reason":"","expectedVersion":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }
}
