package com.erp.manufacturing.module.auth.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.auth.dto.AuthResponse;
import com.erp.manufacturing.module.auth.dto.ChallengeRequest;
import com.erp.manufacturing.module.auth.dto.ChallengeResponse;
import com.erp.manufacturing.module.auth.dto.ChangePasswordRequest;
import com.erp.manufacturing.module.auth.dto.CompleteChallengeRequest;
import com.erp.manufacturing.module.auth.dto.ForgotPasswordRequest;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.auth.dto.LoginResult;
import com.erp.manufacturing.module.auth.dto.LogoutRequest;
import com.erp.manufacturing.module.auth.dto.MeResponse;
import com.erp.manufacturing.module.auth.dto.RefreshRequest;
import com.erp.manufacturing.module.auth.dto.ResetPasswordRequest;
import com.erp.manufacturing.module.auth.dto.VerifyChallengeRequest;
import com.erp.manufacturing.module.auth.dto.VerifyChallengeResponse;
import com.erp.manufacturing.module.auth.service.AuthService;
import com.erp.manufacturing.module.auth.service.PasswordFlowService;
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
    private final PasswordFlowService passwordFlowService;

    @PostMapping("/v1/login")
    @Operation(summary = "Login and receive access/refresh token pair",
            description = "An account still awaiting first-login setup gets `result.kind = PASSWORD_SETUP` "
                    + "and a challenge instead of tokens; the emailed code goes to /password-challenges/verify.")
    public ResponseEntity<ApiResponse<LoginResult>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        LoginResult result = authService.login(request, httpRequest);
        if (!(result instanceof AuthResponse authResponse)) {
            return ResponseEntity.ok(ApiResponse.ok(result, "Password setup required."));
        }

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

    // ── Email-code password flows (setup, forgot-password) and change ──────
    // Idempotency-Key is accepted but not required: replay safety comes from each step's own state
    // (see PasswordFlowService).

    @PostMapping("/v1/password-challenges")
    @Operation(summary = "Request (or resend) an emailed 6-digit code",
            description = "SETUP needs `previousId` from the login that returned PASSWORD_SETUP; RESET answers "
                    + "identically for unknown identifiers. 429 RESEND_TOO_SOON carries Retry-After.")
    public ResponseEntity<ApiResponse<ChallengeResponse>> requestChallenge(
            @Valid @RequestBody ChallengeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(passwordFlowService.request(request)));
    }

    @PostMapping("/v1/password-challenges/verify")
    @Operation(summary = "Exchange the emailed code for a single-use setup token",
            description = "CODE_INVALID / CODE_EXPIRED / CODE_USED, or 429 CODE_ATTEMPTS after 5 wrong codes.")
    public ResponseEntity<ApiResponse<VerifyChallengeResponse>> verifyChallenge(
            @Valid @RequestBody VerifyChallengeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(passwordFlowService.verify(request)));
    }

    @PostMapping("/v1/password-challenges/complete")
    @Operation(summary = "Set the new password with the setup token",
            description = "Activates a PENDING_SETUP account and revokes every session; the user logs in again.")
    public ResponseEntity<ApiResponse<Void>> completeChallenge(
            @Valid @RequestBody CompleteChallengeRequest request,
            HttpServletRequest httpRequest) {
        passwordFlowService.complete(request,
                (String) httpRequest.getAttribute("clientIp"), (String) httpRequest.getAttribute("traceId"));
        return ResponseEntity.ok(ApiResponse.noContent("Password set successfully"));
    }

    @PostMapping("/v1/password/change")
    @Operation(summary = "Change the password with the current password",
            description = "Requires a session. Revokes every session, including this one.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordFlowService.change((String) httpRequest.getAttribute("authenticatedUserId"), request,
                (String) httpRequest.getAttribute("clientIp"), (String) httpRequest.getAttribute("traceId"));
        return ResponseEntity.ok(ApiResponse.noContent("Password changed successfully"));
    }
}
