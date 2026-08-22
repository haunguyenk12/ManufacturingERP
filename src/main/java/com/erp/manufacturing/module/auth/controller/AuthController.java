package com.erp.manufacturing.module.auth.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.auth.dto.AuthResponse;
import com.erp.manufacturing.module.auth.dto.ForgotPasswordRequest;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.auth.dto.LogoutRequest;
import com.erp.manufacturing.module.auth.dto.MeResponse;
import com.erp.manufacturing.module.auth.dto.RefreshRequest;
import com.erp.manufacturing.module.auth.dto.ResetPasswordRequest;
import com.erp.manufacturing.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints.
 *
 * <p>Base path: /api/auth/v1/. Every endpoint is permit-all in {@code SecurityConfig} except
 * {@code /me}, which requires a valid, unexpired token (see {@code SecurityConfig} for the matcher
 * that carves it out) — {@code forgot-password}/{@code reset-password} (D8c) are unauthenticated by
 * nature, same as {@code login}/{@code refresh}.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, logout, token refresh")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/v1/login")
    @Operation(summary = "Login and receive access/refresh token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        AuthResponse authResponse = authService.login(request, httpRequest);

        // Notify client if previous session was kicked
        if (authResponse.sessionKicked()) {
            httpResponse.setHeader("X-Session-Warning", "previous-session-terminated");
        }

        return ResponseEntity.ok(ApiResponse.ok(authResponse,
                authResponse.sessionKicked()
                        ? "Login successful. Previous session was terminated."
                        : "Login successful."));
    }

    @PostMapping("/v1/refresh")
    @Operation(summary = "Refresh access token using refresh token (rotation)")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request, httpRequest)));
    }

    @PostMapping("/v1/logout")
    @Operation(summary = "Logout current session")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request,
            HttpServletRequest httpRequest) {
        authService.logout(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.noContent("Logged out successfully"));
    }

    @PostMapping("/v1/logout-all")
    @Operation(summary = "Logout from all devices (revoke all sessions)")
    public ResponseEntity<ApiResponse<Void>> logoutAll(HttpServletRequest httpRequest) {
        authService.logoutAll(httpRequest);
        return ResponseEntity.ok(ApiResponse.noContent("Logged out from all devices"));
    }

    @GetMapping("/v1/me")
    @Operation(summary = "Get current user profile, permissions, and accessible scopes")
    public ResponseEntity<ApiResponse<MeResponse>> me(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(httpRequest)));
    }

    @PostMapping("/v1/forgot-password")
    @Operation(summary = "Request a password reset link",
            description = "Always returns 200 with the same generic message, whether or not the "
                    + "email belongs to a real account (account enumeration prevention).")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.noContent(
                "If this email exists, a reset link has been sent"));
    }

    @PostMapping("/v1/reset-password")
    @Operation(summary = "Reset password using a reset token",
            description = "401 RESET_TOKEN_INVALID if the token is unknown or expired. On success, "
                    + "every existing session for the account is logged out.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        authService.resetPassword(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.noContent("Password reset successfully"));
    }
}
