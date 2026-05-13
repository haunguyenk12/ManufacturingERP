package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Error codes for all authentication and token-related failures.
 * Format: {@code AUTH_XXX}
 */
public enum AuthErrorCode implements ErrorCode {

    // ── Credentials ────────────────────────────────────────────────────────
    INVALID_CREDENTIALS     ("AUTH_001", "Invalid username or password",              HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED          ("AUTH_002", "Account is temporarily locked",             HttpStatus.LOCKED),
    ACCOUNT_INACTIVE        ("AUTH_003", "Account is inactive",                       HttpStatus.FORBIDDEN),

    // ── Token ──────────────────────────────────────────────────────────────
    TOKEN_EXPIRED           ("AUTH_010", "Access token has expired",                  HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED           ("AUTH_011", "Token has been revoked",                    HttpStatus.UNAUTHORIZED),
    TOKEN_MALFORMED         ("AUTH_012", "Token is malformed or invalid",             HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED   ("AUTH_013", "Refresh token has expired or is invalid",   HttpStatus.UNAUTHORIZED),

    // ── Session ────────────────────────────────────────────────────────────
    SESSION_CONFLICT        ("AUTH_020", "Session conflict detected",                 HttpStatus.CONFLICT),

    // ── Authorization ──────────────────────────────────────────────────────
    ACCESS_DENIED           ("AUTH_030", "Insufficient permissions",                  HttpStatus.FORBIDDEN);

    private final String     code;
    private final String     message;
    private final HttpStatus httpStatus;

    AuthErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code       = code;
        this.message    = message;
        this.httpStatus = httpStatus;
    }

    @Override public String     code()    { return code; }
    @Override public String     message() { return message; }
    @Override public HttpStatus status()  { return httpStatus; }
}
