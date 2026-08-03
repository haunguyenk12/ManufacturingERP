package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Error codes for all authentication and token-related failures.
 * The wire value is the enum constant name so clients branch on a stable, readable code
 * (see {@code .claude/rules/error-handling.md} §5.3).
 */
public enum AuthErrorCode implements ErrorCode {

    // ── Credentials ────────────────────────────────────────────────────────
    INVALID_CREDENTIALS     ("INVALID_CREDENTIALS",    "Invalid username or password",              HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED          ("ACCOUNT_LOCKED",         "Account is temporarily locked",             HttpStatus.LOCKED),
    ACCOUNT_INACTIVE        ("ACCOUNT_INACTIVE",       "Account is inactive",                       HttpStatus.FORBIDDEN),

    // ── Token ──────────────────────────────────────────────────────────────
    TOKEN_EXPIRED           ("TOKEN_EXPIRED",          "Access token has expired",                  HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED           ("TOKEN_REVOKED",          "Token has been revoked",                    HttpStatus.UNAUTHORIZED),
    TOKEN_MALFORMED         ("TOKEN_MALFORMED",        "Token is malformed or invalid",             HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED   ("REFRESH_TOKEN_EXPIRED",  "Refresh token has expired or is invalid",   HttpStatus.UNAUTHORIZED),
    /** RTR (B80): a refresh token that was already rotated away came back — treated as stolen. */
    TOKEN_REUSE_DETECTED    ("TOKEN_REUSE_DETECTED",   "Suspicious activity detected. Please login again.", HttpStatus.UNAUTHORIZED),

    // ── Session ────────────────────────────────────────────────────────────
    SESSION_CONFLICT        ("SESSION_CONFLICT",       "Session conflict detected",                 HttpStatus.CONFLICT),

    // ── Authorization ──────────────────────────────────────────────────────
    ACCESS_DENIED           ("PERMISSION_DENIED",      "Insufficient permissions",                  HttpStatus.FORBIDDEN);

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
