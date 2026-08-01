package com.erp.manufacturing.module.organization.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.organization.dto.RoleResponse;
import com.erp.manufacturing.module.organization.dto.UserRoleAssignmentRequest;
import com.erp.manufacturing.module.organization.dto.UserRoleAssignmentResponse;
import com.erp.manufacturing.module.organization.service.AccessControlService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link AccessControlController} (`D7b`, group B).
 *
 * <p>Assigning a role touches three pieces of master data (user, role, access scope) and each has
 * its own failure code. The two that share HTTP 409 — a duplicate active assignment
 * ({@code RESOURCE_ALREADY_EXISTS}) — and the two that stay 422 — an inactive role or scope
 * ({@code OPERATION_NOT_ALLOWED}, §5.3 master data) — are asserted separately, because status alone
 * cannot distinguish them.
 *
 * <p>RBAC evaluation itself (scope inheritance, expiry) is invariant B30–B32 territory and is
 * covered by {@code PermissionGuardTest} and {@code UserRoleAssignmentRepositoryIT}.
 */
@WebMvcTest(controllers = AccessControlController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AccessControlController – response envelope contract")
class AccessControlControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AccessControlService accessControlService;

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

    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();
    private static final UUID USER_ID       = UUID.randomUUID();
    private static final UUID ROLE_ID       = UUID.randomUUID();
    private static final UUID SCOPE_ID      = UUID.randomUUID();
    private static final UUID PERMISSION_ID = UUID.randomUUID();

    private UserRoleAssignmentResponse sampleAssignment() {
        return new UserRoleAssignmentResponse(ASSIGNMENT_ID, USER_ID, ROLE_ID, SCOPE_ID, "ACTIVE", null);
    }

    private RoleResponse sampleRole() {
        return new RoleResponse(ROLE_ID, null, "PLANNER", "Production Planner",
                "Runs MRP and manages planning demands", false, "ACTIVE");
    }

    private String assignBody() {
        return """
                {"userId":"%s","roleId":"%s","scopeId":"%s"}
                """.formatted(USER_ID, ROLE_ID, SCOPE_ID);
    }

    @Test
    @DisplayName("assignRole: valid request returns 201 with the ACTIVE assignment")
    void assignRole_validRequest_returns201Created() throws Exception {
        when(accessControlService.assignRole(any(UserRoleAssignmentRequest.class))).thenReturn(sampleAssignment());

        mockMvc.perform(post("/api/v1/access/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.assignmentId").value(ASSIGNMENT_ID.toString()))
                .andExpect(jsonPath("$.result.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("assignRole: missing scopeId returns 400 VALIDATION_ERROR with the field name")
    void assignRole_missingScopeId_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/access/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","roleId":"%s"}
                                """.formatted(USER_ID, ROLE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("scopeId"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(accessControlService);
    }

    @Test
    @DisplayName("assignRole: inactive role stays 422 OPERATION_NOT_ALLOWED (§5.3 master data)")
    void assignRole_inactiveRole_returns422OperationNotAllowed() throws Exception {
        when(accessControlService.assignRole(any(UserRoleAssignmentRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Role is inactive: " + ROLE_ID));

        mockMvc.perform(post("/api/v1/access/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody()))
                .andExpect(status().is(BusinessErrorCode.OPERATION_NOT_ALLOWED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPERATION_NOT_ALLOWED.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("assignRole: inactive access scope stays 422 OPERATION_NOT_ALLOWED (§5.3 master data)")
    void assignRole_inactiveScope_returns422OperationNotAllowed() throws Exception {
        when(accessControlService.assignRole(any(UserRoleAssignmentRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Access scope is inactive: " + SCOPE_ID));

        mockMvc.perform(post("/api/v1/access/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody()))
                .andExpect(status().is(BusinessErrorCode.OPERATION_NOT_ALLOWED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPERATION_NOT_ALLOWED.code()));
    }

    @Test
    @DisplayName("assignRole: an existing active assignment returns 409 RESOURCE_ALREADY_EXISTS")
    void assignRole_duplicateActiveAssignment_returns409AlreadyExists() throws Exception {
        when(accessControlService.assignRole(any(UserRoleAssignmentRequest.class)))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Active role assignment already exists: " + ASSIGNMENT_ID));

        mockMvc.perform(post("/api/v1/access/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody()))
                .andExpect(status().is(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code()));
    }

    @Test
    @DisplayName("listRoles: PageResult envelope (page/size/totalElements/totalPages/first/last) "
            + "is part of the contract")
    void listRoles_returns200WithFullPageEnvelope() throws Exception {
        when(accessControlService.listRoles(any()))
                .thenReturn(new PageResult<>(List.of(sampleRole()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/access/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].code").value("PLANNER"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("grantPermission: returns 200 with a null result payload (§5.8 no-content pattern)")
    void grantPermission_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/access/roles/" + ROLE_ID + "/permissions/" + PERMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(accessControlService).grantPermission(ROLE_ID, PERMISSION_ID);
    }
}
