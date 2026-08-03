package com.erp.manufacturing.module.auth.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.module.auth.dto.AuthResponse;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.auth.dto.LogoutRequest;
import com.erp.manufacturing.module.auth.dto.MeResponse;
import com.erp.manufacturing.module.auth.dto.RefreshRequest;
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
 * <p>Base path: /api/v1/auth/. {@code login}/{@code refresh}/{@code logout}/{@code logout-all} are
 * permit-all in {@code SecurityConfig}; {@code /me} is the one exception — it requires a valid,
 * unexpired token (see {@code SecurityConfig} for the matcher that carves it out).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, logout, token refresh")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
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

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token (rotation)")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request, httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current session")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request,
            HttpServletRequest httpRequest) {
        authService.logout(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.noContent("Logged out successfully"));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Logout from all devices (revoke all sessions)")
    public ResponseEntity<ApiResponse<Void>> logoutAll(HttpServletRequest httpRequest) {
        authService.logoutAll(httpRequest);
        return ResponseEntity.ok(ApiResponse.noContent("Logged out from all devices"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile, permissions, and accessible scopes")
    public ResponseEntity<ApiResponse<MeResponse>> me(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(httpRequest)));
    }
}
