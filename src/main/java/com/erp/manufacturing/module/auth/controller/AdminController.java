package com.erp.manufacturing.module.auth.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-only account recovery actions (D8c). Authorization is enforced on
 * {@link AuthService#adminUnlockAccount} via {@code @PreAuthorize}, the same pattern
 * {@code UserService} uses for its own ADMIN-only endpoints.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative account recovery actions")
public class AdminController {

    private final AuthService authService;

    @PatchMapping("/{userId}/unlock")
    @Operation(summary = "Manually unlock a user account (ADMIN only)",
            description = "Clears the Redis brute-force fail-counter and reactivates the account.")
    public ResponseEntity<ApiResponse<Void>> unlock(@PathVariable UUID userId) {
        authService.adminUnlockAccount(userId);
        return ResponseEntity.ok(ApiResponse.noContent("Account unlocked successfully"));
    }
}
