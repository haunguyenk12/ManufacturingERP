package com.erp.manufacturing.common.audit.controller;

import com.erp.manufacturing.common.audit.AuditLogQueryService;
import com.erp.manufacturing.common.audit.dto.AuditLogDetailResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogResponse;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link AuditLogController} (C2-1) — envelope contract only;
 * {@code @PreAuthorize} itself is covered by {@code AuditLogMethodSecurityTest} (rule R2 separation).
 */
@WebMvcTest(controllers = AuditLogController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuditLogController – response envelope contract")
class AuditLogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuditLogQueryService auditLogQueryService;

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

    @Test
    @DisplayName("list: default request returns 200 with paginated envelope")
    void list_defaultRequest_returns200() throws Exception {
        UUID auditId = UUID.randomUUID();
        AuditLogResponse response = new AuditLogResponse(
                auditId, UUID.randomUUID(), "admin", "LOGIN", null, null, null, "SUCCESS",
                "127.0.0.1", "curl/8.0", "trace-1", null, Instant.parse("2026-08-06T10:00:00Z"));
        when(auditLogQueryService.list(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.from(
                        new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].auditId").value(auditId.toString()))
                .andExpect(jsonPath("$.result.content[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("list: invalid action query param returns 400 VALIDATION_ERROR")
    void list_invalidActionParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs").param("action", "NOT_A_REAL_ACTION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
    }

    @Test
    @DisplayName("get: known id returns 200 with the detail envelope including changes[]")
    void get_knownId_returns200WithChanges() throws Exception {
        UUID auditId = UUID.randomUUID();
        AuditLogDetailResponse response = new AuditLogDetailResponse(
                auditId, UUID.randomUUID(), "admin", "WORK_ORDER_UPDATED", "WorkOrder", "wo-1",
                null, "SUCCESS", "127.0.0.1", "curl/8.0", "trace-1", null,
                Instant.parse("2026-08-06T10:00:00Z"), List.of());
        when(auditLogQueryService.get(auditId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/audit-logs/" + auditId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.auditId").value(auditId.toString()))
                .andExpect(jsonPath("$.result.changes").isArray());
    }

    @Test
    @DisplayName("get: unknown id returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404() throws Exception {
        UUID auditId = UUID.randomUUID();
        when(auditLogQueryService.get(auditId)).thenThrow(new AppException(
                ValidationErrorCode.RESOURCE_NOT_FOUND, "Audit log not found: " + auditId));

        mockMvc.perform(get("/api/v1/audit-logs/" + auditId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }
}
