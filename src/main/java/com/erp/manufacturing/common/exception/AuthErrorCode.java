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
    /**
     * P0 auth fix: distinct from {@link #TOKEN_MALFORMED} — this fires only when NO credentials were
     * presented at all (no {@code Authorization} header / not {@code Bearer}-prefixed), thrown by
     * {@link com.erp.manufacturing.common.security.JwtAuthEntryPoint}. Any *presented* token, malformed
     * or not, is caught and answered inside {@code JwtAuthenticationFilter} before reaching this code
     * path, so the two codes are never ambiguous about which case actually happened.
     */
    AUTHENTICATION_REQUIRED ("AUTHENTICATION_REQUIRED", "Authentication is required to access this resource", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED   ("REFRESH_TOKEN_EXPIRED",  "Refresh token has expired or is invalid",   HttpStatus.UNAUTHORIZED),
    /** RTR (B80): a refresh token that was already rotated away came back — treated as stolen. */
    TOKEN_REUSE_DETECTED    ("TOKEN_REUSE_DETECTED",   "Suspicious activity detected. Please login again.", HttpStatus.UNAUTHORIZED),

    // ── Session ────────────────────────────────────────────────────────────
    /**
     * B81 (D8b): the session behind this refresh token has been alive longer than the absolute
     * timeout, so it is retired regardless of how recently it was used. Shares HTTP 401 with
     * {@link #REFRESH_TOKEN_EXPIRED} and {@link #TOKEN_REUSE_DETECTED} — the {@code code} is the
     * only thing telling the three apart, which is what clients must branch on.
     */
    SESSION_ABSOLUTE_TIMEOUT("SESSION_ABSOLUTE_TIMEOUT", "Session expired. Please login again.",    HttpStatus.UNAUTHORIZED),

    // ── Account Recovery (D8c) ────────────────────────────────────────────
    /**
     * D8c: covers both "token never existed" and "token expired" — Redis TTL makes the two
     * indistinguishable, same as {@link #REFRESH_TOKEN_EXPIRED} for refresh tokens.
     */
    RESET_TOKEN_INVALID     ("RESET_TOKEN_INVALID",    "Reset token is invalid or has expired",     HttpStatus.UNAUTHORIZED),

    // ── Password setup / change (email code) ───────────────────────────────
    /** 403: the caller skipped the initial-password login that must precede a SETUP challenge. */
    PASSWORD_SETUP_REQUIRED ("PASSWORD_SETUP_REQUIRED", "Sign in with the initial password first",  HttpStatus.FORBIDDEN),
    CODE_INVALID            ("CODE_INVALID",           "The code is incorrect",                     HttpStatus.BAD_REQUEST),
    CODE_EXPIRED            ("CODE_EXPIRED",           "The code has expired",                      HttpStatus.UNPROCESSABLE_ENTITY),
    CODE_USED               ("CODE_USED",              "The code has already been used",            HttpStatus.UNPROCESSABLE_ENTITY),
    CODE_ATTEMPTS           ("CODE_ATTEMPTS",          "Too many incorrect codes",                  HttpStatus.TOO_MANY_REQUESTS),
    RESEND_TOO_SOON         ("RESEND_TOO_SOON",        "A code was sent recently. Wait before requesting another", HttpStatus.TOO_MANY_REQUESTS),
    /**
     * 422, not 401: the caller is typing a password into a form, and a 401 on an authenticated call
     * (change) is what makes a client end the session.
     */
    CURRENT_PASSWORD_INVALID("CURRENT_PASSWORD_INVALID", "The current password is incorrect",       HttpStatus.UNPROCESSABLE_ENTITY),
    PASSWORD_POLICY         ("PASSWORD_POLICY",        "The new password does not meet the password policy", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Covers unknown, expired and already-consumed setup tokens — the store cannot tell them apart. */
    SETUP_TOKEN_INVALID     ("SETUP_TOKEN_INVALID",    "The setup token is invalid or has expired", HttpStatus.UNPROCESSABLE_ENTITY),

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
