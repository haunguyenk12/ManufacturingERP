package com.erp.manufacturing.module.user.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.user.dto.CreateUserRequest;
import com.erp.manufacturing.module.user.dto.UpdateUserRequest;
import com.erp.manufacturing.module.user.dto.UserResponse;
import com.erp.manufacturing.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User management REST controller.
 * Authorization is handled at the service layer via {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management endpoints")
public class UserController {

    private final UserService userService;

    @GetMapping("/v1")
    @Operation(summary = "List all users (ADMIN only)")
    public ResponseEntity<ApiResponse<PageResult<UserResponse>>> list(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        return ResponseEntity.ok(ApiResponse.ok(
                userService.findAll(PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(userId)));
    }

    @PostMapping("/v1")
    @Operation(summary = "Create new user (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(created));
    }

    @PatchMapping("/v1/{userId}")
    @Operation(summary = "Update user")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.update(userId, request)));
    }

    @DeleteMapping("/v1/{userId}")
    @Operation(summary = "Deactivate user (ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID userId) {
        userService.delete(userId);
        return ResponseEntity.ok(ApiResponse.noContent("User deactivated successfully"));
    }

    @PostMapping("/v1/{userId}/roles/{roleName}")
    @Operation(summary = "Assign role to user (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> assignRole(
            @PathVariable UUID userId,
            @PathVariable String roleName) {
        return ResponseEntity.ok(ApiResponse.ok(userService.assignRole(userId, roleName)));
    }

    @DeleteMapping("/v1/{userId}/roles/{roleName}")
    @Operation(summary = "Revoke role from user (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> revokeRole(
            @PathVariable UUID userId,
            @PathVariable String roleName) {
        return ResponseEntity.ok(ApiResponse.ok(userService.revokeRole(userId, roleName)));
    }
}
