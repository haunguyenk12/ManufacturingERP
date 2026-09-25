package com.erp.manufacturing.common.audit.controller;

import com.erp.manufacturing.common.audit.AuditLogQueryService;
import com.erp.manufacturing.common.audit.dto.AuditLogChangeResponse;
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

import static org.hamcrest.Matchers.nullValue;
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
                auditId, UUID.randomUUID(), "admin", "LOGIN", "Uom", "uom-1", "Piece", null,
                "SUCCESS", "LOGIN_OK", "AUTH", "127.0.0.1", "curl/8.0", "trace-1",
                "POST", "/api/auth/v1/login", null, null, null,
                Instant.parse("2026-08-06T10:00:00Z"), "SUCCESS", Instant.parse("2026-08-06T10:00:00Z"));
        when(auditLogQueryService.list(any(), any()))
                .thenReturn(PageResult.from(
                        new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].auditId").value(auditId.toString()))
                .andExpect(jsonPath("$.result.content[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.result.content[0].entityName").value("Piece"))
                // AR-6 restored entityId: without a stable key a client can see WHICH KIND of object
                // an event was about but has nothing to link to or filter by. The previous assertion
                // pinned that gap; it now pins the fix.
                .andExpect(jsonPath("$.result.content[0].entityId").value("uom-1"))
                .andExpect(jsonPath("$.result.content[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].source").value("AUTH"))
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
        mockMvc.perform(get("/v1/audit-logs").param("action", "NOT_A_REAL_ACTION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
    }

    @Test
    @DisplayName("get: known id returns 200 with the detail envelope including changes[]")
    void get_knownId_returns200WithChanges() throws Exception {
        UUID auditId = UUID.randomUUID();
        AuditLogChangeResponse change = new AuditLogChangeResponse(
                UUID.randomUUID(), "status", "DRAFT",
                "RELEASED", "UPDATE", Instant.parse("2026-08-06T10:00:00Z"));
        AuditLogDetailResponse response = new AuditLogDetailResponse(
                auditId, UUID.randomUUID(), "admin", "WORK_ORDER_UPDATED", "WorkOrder", "wo-1",
                "WO-2026-001", null, "SUCCESS", null, "HTTP", "127.0.0.1", "curl/8.0", "trace-1",
                "POST", "/api/v1/work-orders", null, null, null, null,
                Instant.parse("2026-08-06T10:00:00Z"), "SUCCESS", Instant.parse("2026-08-06T10:00:00Z"),
                List.of(new com.erp.manufacturing.common.audit.dto.AuditLogEntityResponse(
                        "PRIMARY", "WorkOrder", "wo-1", "WO-2026-001")),
                List.of(change));
        when(auditLogQueryService.get(auditId)).thenReturn(response);

        mockMvc.perform(get("/v1/audit-logs/" + auditId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.auditId").value(auditId.toString()))
                .andExpect(jsonPath("$.result.entityName").value("WO-2026-001"))
                .andExpect(jsonPath("$.result.entityId").value("wo-1"))
                // audit_log_entities: the targets a single entityType/entityId pair cannot express.
                .andExpect(jsonPath("$.result.entities[0].relation").value("PRIMARY"))
                .andExpect(jsonPath("$.result.changes").isArray())
                .andExpect(jsonPath("$.result.changes[0].fieldName").value("status"))
                .andExpect(jsonPath("$.result.changes[0].oldValue").value("DRAFT"))
                .andExpect(jsonPath("$.result.changes[0].newValue").value("RELEASED"));
    }

    /**
     * The frontend crash of {@code BACKEND_AUDIT_LOG_VALUE_CONTRACT_2026-08-25.md} happened here, at the
     * HTTP layer: a structured snapshot reached React as a JSON object. {@code isString()} is the
     * assertion that matters — {@code value(...)} alone would also pass for an object node.
     */
    @Test
    @DisplayName("get: a structured snapshot is serialised as a JSON string, never as a nested object")
    void get_structuredSnapshot_isSerialisedAsAString() throws Exception {
        UUID auditId = UUID.randomUUID();
        String snapshot = "{\"uom\":\"EA\",\"lineNo\":1,\"componentItemCode\":\"D26-RM-CHAINRING\"}";
        AuditLogChangeResponse change = new AuditLogChangeResponse(
                UUID.randomUUID(), "componentRequirements", null, snapshot, "CREATE",
                Instant.parse("2026-08-25T10:00:00Z"));
        AuditLogDetailResponse response = new AuditLogDetailResponse(
                auditId, UUID.randomUUID(), "admin", "WORK_ORDER_CREATED", "WorkOrder", "wo-1",
                "WO-2026-001", null, "SUCCESS", null, "HTTP", "127.0.0.1", "curl/8.0", "trace-1",
                "POST", "/api/v1/work-orders", null, null, null, null,
                Instant.parse("2026-08-25T10:00:00Z"), "SUCCESS", Instant.parse("2026-08-25T10:00:00Z"),
                List.of(), List.of(change));
        when(auditLogQueryService.get(auditId)).thenReturn(response);

        mockMvc.perform(get("/v1/audit-logs/" + auditId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.changes[0].newValue").isString())
                .andExpect(jsonPath("$.result.changes[0].newValue").value(snapshot))
                .andExpect(jsonPath("$.result.changes[0].oldValue").value(nullValue()));
    }

    @Test
    @DisplayName("get: unknown id returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404() throws Exception {
        UUID auditId = UUID.randomUUID();
        when(auditLogQueryService.get(auditId)).thenThrow(new AppException(
                ValidationErrorCode.RESOURCE_NOT_FOUND, "Audit log not found: " + auditId));

        mockMvc.perform(get("/v1/audit-logs/" + auditId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }
}
