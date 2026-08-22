package com.erp.manufacturing.module.organization.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.organization.dto.AccessScopeResourceResponse;
import com.erp.manufacturing.module.organization.dto.AccessScopeResponse;
import com.erp.manufacturing.module.organization.dto.PermissionResponse;
import com.erp.manufacturing.module.organization.dto.RoleResponse;
import com.erp.manufacturing.module.organization.dto.UserRoleAssignmentRequest;
import com.erp.manufacturing.module.organization.dto.UserRoleAssignmentResponse;
import com.erp.manufacturing.module.organization.service.AccessControlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    private AccessScopeResponse sampleScope() {
        return new AccessScopeResponse(SCOPE_ID, "SCOPE-01", "Plant scope", "PLANT", null, "ACTIVE");
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

        mockMvc.perform(post("/access/v1/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.assignmentId").value(ASSIGNMENT_ID.toString()))
                .andExpect(jsonPath("$.result.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    /**
     * Regression guard for the {@code expiresAt} report of 2026-08-13: the frontend sent a future
     * UTC instant and read back an empty expiry column. The value was never lost — it was leaving as
     * an epoch <em>number</em> ({@code 1787245199.0}), which the client's date adapter turned into
     * null (root cause: debt #27, fixed 2026-08-12; see {@code CLAUDE.md §0.42}).
     *
     * <p>So this asserts the two halves that belong to this endpoint: the instant is bound off the
     * request body (captured, not inferred from a stub), and it comes back out as an ISO string.
     * The mapper configuration that produced the number lives outside any controller slice and is
     * pinned separately by {@code JsonWireFormatTest}.
     */
    @Test
    @DisplayName("assignRole: expiresAt is bound from the body and echoed back as an ISO instant")
    void assignRole_withExpiresAt_bindsTheInstantAndReturnsItAsAnIsoString() throws Exception {
        Instant expiresAt = Instant.parse("2026-08-20T16:59:59Z");
        when(accessControlService.assignRole(any(UserRoleAssignmentRequest.class)))
                .thenReturn(new UserRoleAssignmentResponse(
                        ASSIGNMENT_ID, USER_ID, ROLE_ID, SCOPE_ID, "ACTIVE", expiresAt));

        mockMvc.perform(post("/access/v1/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","roleId":"%s","scopeId":"%s","expiresAt":"2026-08-20T16:59:59.000Z"}
                                """.formatted(USER_ID, ROLE_ID, SCOPE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.expiresAt").value("2026-08-20T16:59:59Z"));

        ArgumentCaptor<UserRoleAssignmentRequest> captor =
                ArgumentCaptor.forClass(UserRoleAssignmentRequest.class);
        verify(accessControlService).assignRole(captor.capture());
        assertThat(captor.getValue().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("assignRole: missing scopeId returns 400 VALIDATION_ERROR with the field name")
    void assignRole_missingScopeId_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/access/v1/assignments")
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

        mockMvc.perform(post("/access/v1/assignments")
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

        mockMvc.perform(post("/access/v1/assignments")
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

        mockMvc.perform(post("/access/v1/assignments")
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

        mockMvc.perform(get("/access/v1/roles"))
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
    @DisplayName("getRole: returns 200 with the role")
    void getRole_returns200() throws Exception {
        when(accessControlService.getRole(ROLE_ID)).thenReturn(sampleRole());

        mockMvc.perform(get("/access/v1/roles/" + ROLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.code").value("PLANNER"));
    }

    @Test
    @DisplayName("updateRole: returns 200 with the updated role")
    void updateRole_returns200() throws Exception {
        when(accessControlService.updateRole(eq(ROLE_ID), any())).thenReturn(sampleRole());

        mockMvc.perform(patch("/access/v1/roles/" + ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("deactivateRole: system role returns 422 OPERATION_NOT_ALLOWED")
    void deactivateRole_systemRole_returns422() throws Exception {
        when(accessControlService.deactivateRole(ROLE_ID))
                .thenThrow(new AppException(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "System role cannot be deactivated: " + ROLE_ID));

        mockMvc.perform(post("/access/v1/roles/" + ROLE_ID + "/deactivate"))
                .andExpect(status().is(BusinessErrorCode.OPERATION_NOT_ALLOWED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPERATION_NOT_ALLOWED.code()));
    }

    @Test
    @DisplayName("activateRole: returns 200 with ACTIVE status")
    void activateRole_returns200() throws Exception {
        when(accessControlService.activateRole(ROLE_ID)).thenReturn(sampleRole());

        mockMvc.perform(post("/access/v1/roles/" + ROLE_ID + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("getScope: returns 200 with the scope")
    void getScope_returns200() throws Exception {
        when(accessControlService.getScope(SCOPE_ID)).thenReturn(sampleScope());

        mockMvc.perform(get("/access/v1/scopes/" + SCOPE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.code").value("SCOPE-01"));
    }

    @Test
    @DisplayName("updateScope: returns 200 with the updated scope")
    void updateScope_returns200() throws Exception {
        when(accessControlService.updateScope(eq(SCOPE_ID), any())).thenReturn(sampleScope());

        mockMvc.perform(patch("/access/v1/scopes/" + SCOPE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Scope Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("deactivateScope: returns 200 with INACTIVE status")
    void deactivateScope_returns200() throws Exception {
        when(accessControlService.deactivateScope(SCOPE_ID)).thenReturn(sampleScope());

        mockMvc.perform(post("/access/v1/scopes/" + SCOPE_ID + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("activateScope: returns 200")
    void activateScope_returns200() throws Exception {
        when(accessControlService.activateScope(SCOPE_ID)).thenReturn(sampleScope());

        mockMvc.perform(post("/access/v1/scopes/" + SCOPE_ID + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("listAssignments: PageResult envelope, filters forwarded to the service")
    void listAssignments_returns200WithFullPageEnvelope() throws Exception {
        when(accessControlService.listAssignments(eq(USER_ID), isNull(), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(sampleAssignment()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/access/v1/assignments").param("userId", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        verify(accessControlService).listAssignments(eq(USER_ID), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("grantPermission: returns 200 with a null result payload (§5.8 no-content pattern)")
    void grantPermission_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(post("/access/v1/roles/" + ROLE_ID + "/permissions/" + PERMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(accessControlService).grantPermission(ROLE_ID, PERMISSION_ID);
    }

    // ── membership reads (FE RBAC contract) ────────────────────────────────

    @Test
    @DisplayName("listRolePermissions: returns the paginated membership envelope in full")
    void listRolePermissions_returns200WithPageResultEnvelope() throws Exception {
        PermissionResponse permission = new PermissionResponse(
                PERMISSION_ID, "PERM_INVENTORY_READ", "INVENTORY", "READ", "Read inventory", "ACTIVE");
        when(accessControlService.listRolePermissions(eq(ROLE_ID), any()))
                .thenReturn(new PageResult<>(List.of(permission), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/access/v1/roles/" + ROLE_ID + "/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].permissionId").value(PERMISSION_ID.toString()))
                .andExpect(jsonPath("$.result.content[0].code").value("PERM_INVENTORY_READ"))
                .andExpect(jsonPath("$.result.content[0].resource").value("INVENTORY"))
                .andExpect(jsonPath("$.result.content[0].action").value("READ"))
                .andExpect(jsonPath("$.result.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("listRolePermissions: unknown role surfaces 404 ENTITY_NOT_FOUND, not an empty page")
    void listRolePermissions_unknownRole_returns404() throws Exception {
        when(accessControlService.listRolePermissions(eq(ROLE_ID), any()))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND, "Role not found: " + ROLE_ID));

        mockMvc.perform(get("/access/v1/roles/" + ROLE_ID + "/permissions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("listRolePermissions: a malformed roleId is a 400, never reaching the service")
    void listRolePermissions_malformedRoleId_returns400() throws Exception {
        mockMvc.perform(get("/access/v1/roles/not-a-uuid/permissions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("roleId"));

        verifyNoInteractions(accessControlService);
    }

    @Test
    @DisplayName("listScopeResources: returns the scope's resource membership")
    void listScopeResources_returns200WithMembership() throws Exception {
        UUID scopeResourceId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(accessControlService.listScopeResources(eq(SCOPE_ID), any()))
                .thenReturn(new PageResult<>(List.of(
                        new AccessScopeResourceResponse(scopeResourceId, SCOPE_ID, "PLANT", plantId)),
                        0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/access/v1/scopes/" + SCOPE_ID + "/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].scopeResourceId").value(scopeResourceId.toString()))
                .andExpect(jsonPath("$.result.content[0].resourceType").value("PLANT"))
                .andExpect(jsonPath("$.result.content[0].resourceId").value(plantId.toString()))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    @DisplayName("listRolePermissions: size above the API max is clamped to 100 (A4)")
    void listRolePermissions_sizeAboveMax_isClampedToHundred() throws Exception {
        when(accessControlService.listRolePermissions(eq(ROLE_ID), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0L, 0, true, true));

        mockMvc.perform(get("/access/v1/roles/" + ROLE_ID + "/permissions").param("size", "500"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(accessControlService).listRolePermissions(eq(ROLE_ID), captor.capture());
        // Literal 100, not PageableFactory.MAX_PAGE_SIZE — see D7b.3, the constant on both sides is a tautology.
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
