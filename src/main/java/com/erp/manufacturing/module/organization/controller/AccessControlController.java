package com.erp.manufacturing.module.organization.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.dto.*;
import com.erp.manufacturing.module.organization.service.AccessControlService;
import com.erp.manufacturing.common.web.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/access")
@RequiredArgsConstructor
@Tag(name = "Access Control", description = "Dynamic RBAC and access scopes")
public class AccessControlController {

    private final AccessControlService accessControlService;

    @GetMapping("/v1/roles")
    @Operation(summary = "List roles")
    public ResponseEntity<ApiResponse<PageResult<RoleResponse>>> listRoles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.listRoles(PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/v1/roles")
    @Operation(summary = "Create custom role")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.createRole(request)));
    }

    @GetMapping("/v1/roles/{roleId}")
    @Operation(summary = "Get role")
    public ResponseEntity<ApiResponse<RoleResponse>> getRole(@PathVariable UUID roleId) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.getRole(roleId)));
    }

    @PatchMapping("/v1/roles/{roleId}")
    @Operation(summary = "Update role name/description")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable UUID roleId, @Valid @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.updateRole(roleId, request)));
    }

    @PostMapping("/v1/roles/{roleId}/activate")
    @Operation(summary = "Activate role")
    public ResponseEntity<ApiResponse<RoleResponse>> activateRole(@PathVariable UUID roleId) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.activateRole(roleId)));
    }

    @PostMapping("/v1/roles/{roleId}/deactivate")
    @Operation(summary = "Deactivate role (system roles cannot be deactivated)")
    public ResponseEntity<ApiResponse<RoleResponse>> deactivateRole(@PathVariable UUID roleId) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.deactivateRole(roleId)));
    }

    @PostMapping("/v1/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Grant permission to role")
    public ResponseEntity<ApiResponse<Void>> grantPermission(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        accessControlService.grantPermission(roleId, permissionId);
        return ResponseEntity.ok(ApiResponse.noContent("Permission granted successfully"));
    }

    @DeleteMapping("/v1/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Revoke permission from role")
    public ResponseEntity<ApiResponse<Void>> revokePermission(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        accessControlService.revokePermission(roleId, permissionId);
        return ResponseEntity.ok(ApiResponse.noContent("Permission revoked successfully"));
    }

    /** Declared before the {@code /roles/{roleId}/permissions/{permissionId}} grant mapping for
     *  readability; Spring matches on the number of path segments, so the two never collide. */
    @GetMapping("/v1/roles/{roleId}/permissions")
    @Operation(summary = "List the permissions granted to a role")
    public ResponseEntity<ApiResponse<PageResult<PermissionResponse>>> listRolePermissions(
            @PathVariable UUID roleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(
                accessControlService.listRolePermissions(roleId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/permissions")
    @Operation(summary = "List permissions")
    public ResponseEntity<ApiResponse<PageResult<PermissionResponse>>> listPermissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.listPermissions(PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/v1/permissions")
    @Operation(summary = "Create permission")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody PermissionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.createPermission(request)));
    }

    @GetMapping("/v1/scopes")
    @Operation(summary = "List access scopes")
    public ResponseEntity<ApiResponse<PageResult<AccessScopeResponse>>> listScopes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.listScopes(PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/v1/scopes")
    @Operation(summary = "Create access scope")
    public ResponseEntity<ApiResponse<AccessScopeResponse>> createScope(
            @Valid @RequestBody AccessScopeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.createScope(request)));
    }

    @GetMapping("/v1/scopes/{scopeId}")
    @Operation(summary = "Get access scope")
    public ResponseEntity<ApiResponse<AccessScopeResponse>> getScope(@PathVariable UUID scopeId) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.getScope(scopeId)));
    }

    @PatchMapping("/v1/scopes/{scopeId}")
    @Operation(summary = "Update access scope name/description")
    public ResponseEntity<ApiResponse<AccessScopeResponse>> updateScope(
            @PathVariable UUID scopeId, @Valid @RequestBody AccessScopeUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.updateScope(scopeId, request)));
    }

    @PostMapping("/v1/scopes/{scopeId}/activate")
    @Operation(summary = "Activate access scope")
    public ResponseEntity<ApiResponse<AccessScopeResponse>> activateScope(@PathVariable UUID scopeId) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.activateScope(scopeId)));
    }

    @PostMapping("/v1/scopes/{scopeId}/deactivate")
    @Operation(summary = "Deactivate access scope")
    public ResponseEntity<ApiResponse<AccessScopeResponse>> deactivateScope(@PathVariable UUID scopeId) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.deactivateScope(scopeId)));
    }

    @PostMapping("/v1/scopes/{scopeId}/resources")
    @Operation(summary = "Add resource to access scope")
    public ResponseEntity<ApiResponse<AccessScopeResourceResponse>> addScopeResource(
            @PathVariable UUID scopeId,
            @Valid @RequestBody AccessScopeResourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.addScopeResource(scopeId, request)));
    }

    @GetMapping("/v1/scopes/{scopeId}/resources")
    @Operation(summary = "List the resources an access scope covers")
    public ResponseEntity<ApiResponse<PageResult<AccessScopeResourceResponse>>> listScopeResources(
            @PathVariable UUID scopeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(
                accessControlService.listScopeResources(scopeId, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/v1/assignments")
    @Operation(summary = "Assign role to user within access scope")
    public ResponseEntity<ApiResponse<UserRoleAssignmentResponse>> assignRole(
            @Valid @RequestBody UserRoleAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.assignRole(request)));
    }

    /** Declared before {@code /assignments/{assignmentId}} would otherwise be considered — Spring
     *  matches the literal path first, kept adjacent for readability (same pattern as
     *  {@code SalesOrderController#planningDemands}). */
    @GetMapping("/v1/assignments")
    @Operation(summary = "List role assignments, optionally filtered by user/role/scope")
    public ResponseEntity<ApiResponse<PageResult<UserRoleAssignmentResponse>>> listAssignments(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.listAssignments(
                userId, roleId, scopeId, PageableFactory.of(page, size, "createdAt", "desc"))));
    }

    @DeleteMapping("/v1/assignments/{assignmentId}")
    @Operation(summary = "Revoke scoped role assignment")
    public ResponseEntity<ApiResponse<Void>> revokeAssignment(@PathVariable UUID assignmentId) {
        accessControlService.revokeAssignment(assignmentId);
        return ResponseEntity.ok(ApiResponse.noContent("Role assignment revoked successfully"));
    }

}
