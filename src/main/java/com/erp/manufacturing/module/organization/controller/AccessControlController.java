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
@RequestMapping("/api/v1/access")
@RequiredArgsConstructor
@Tag(name = "Access Control", description = "Dynamic RBAC and access scopes")
public class AccessControlController {

    private final AccessControlService accessControlService;

    @GetMapping("/roles")
    @Operation(summary = "List roles")
    public ResponseEntity<ApiResponse<PageResult<RoleResponse>>> listRoles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.listRoles(PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/roles")
    @Operation(summary = "Create custom role")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.createRole(request)));
    }

    @PostMapping("/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Grant permission to role")
    public ResponseEntity<ApiResponse<Void>> grantPermission(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        accessControlService.grantPermission(roleId, permissionId);
        return ResponseEntity.ok(ApiResponse.noContent("Permission granted successfully"));
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Revoke permission from role")
    public ResponseEntity<ApiResponse<Void>> revokePermission(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        accessControlService.revokePermission(roleId, permissionId);
        return ResponseEntity.ok(ApiResponse.noContent("Permission revoked successfully"));
    }

    @GetMapping("/permissions")
    @Operation(summary = "List permissions")
    public ResponseEntity<ApiResponse<PageResult<PermissionResponse>>> listPermissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.listPermissions(PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/permissions")
    @Operation(summary = "Create permission")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody PermissionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.createPermission(request)));
    }

    @GetMapping("/scopes")
    @Operation(summary = "List access scopes")
    public ResponseEntity<ApiResponse<PageResult<AccessScopeResponse>>> listScopes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(accessControlService.listScopes(PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/scopes")
    @Operation(summary = "Create access scope")
    public ResponseEntity<ApiResponse<AccessScopeResponse>> createScope(
            @Valid @RequestBody AccessScopeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.createScope(request)));
    }

    @PostMapping("/scopes/{scopeId}/resources")
    @Operation(summary = "Add resource to access scope")
    public ResponseEntity<ApiResponse<AccessScopeResourceResponse>> addScopeResource(
            @PathVariable UUID scopeId,
            @Valid @RequestBody AccessScopeResourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.addScopeResource(scopeId, request)));
    }

    @PostMapping("/assignments")
    @Operation(summary = "Assign role to user within access scope")
    public ResponseEntity<ApiResponse<UserRoleAssignmentResponse>> assignRole(
            @Valid @RequestBody UserRoleAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(accessControlService.assignRole(request)));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @Operation(summary = "Revoke scoped role assignment")
    public ResponseEntity<ApiResponse<Void>> revokeAssignment(@PathVariable UUID assignmentId) {
        accessControlService.revokeAssignment(assignmentId);
        return ResponseEntity.ok(ApiResponse.noContent("Role assignment revoked successfully"));
    }

}
