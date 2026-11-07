package com.erp.manufacturing.module.auth.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.auth.service.AuthService;
import com.erp.manufacturing.module.user.dto.CreateAccountCommand;
import com.erp.manufacturing.module.user.dto.UserResponse;
import com.erp.manufacturing.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-only account recovery actions (D8c). Authorization is enforced on
 * {@link AuthService#adminUnlockAccount} via {@code @PreAuthorize}, the same pattern
 * {@code UserService} uses for its own ADMIN-only endpoints.
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative account recovery actions")
public class AdminController {

    private final AuthService authService;
    private final UserService userService;

    @PatchMapping("/v1/{userId}/unlock")
    @Operation(summary = "Manually unlock a user account (ADMIN only)",
            description = "Clears the Redis brute-force fail-counter and reactivates the account.")
    public ResponseEntity<ApiResponse<Void>> unlock(@PathVariable UUID userId) {
        authService.adminUnlockAccount(userId);
        return ResponseEntity.ok(ApiResponse.noContent("Account unlocked successfully"));
    }

    @PostMapping("/v1/accounts/commands")
    @Operation(summary = "Create an account that must set its own password at first login",
            description = "Body `{kind:\"CREATE\", username, email, password}` (the password is the initial one). "
                    + "The account is PENDING_SETUP: login with the initial password returns PASSWORD_SETUP, not tokens. "
                    + "Repeating the identical command returns the same account. Needs PERM_ACCESS_MANAGE at GLOBAL scope.")
    public ResponseEntity<ApiResponse<UserResponse>> accountCommand(
            @Valid @RequestBody CreateAccountCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(userService.createPendingSetup(command)));
    }
}
