package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Error codes for input validation failures.
 * Format: {@code VAL_XXX}
 */
public enum ValidationErrorCode implements ErrorCode {

    // ── Generic ────────────────────────────────────────────────────────────
    INVALID_INPUT           ("VAL_001", "Invalid input data",                            HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_FIELD  ("VAL_002", "Required field is missing",                     HttpStatus.BAD_REQUEST),
    FIELD_TOO_LONG          ("VAL_003", "Field value exceeds maximum length",            HttpStatus.BAD_REQUEST),
    FIELD_FORMAT_INVALID    ("VAL_004", "Field value has an invalid format",             HttpStatus.BAD_REQUEST),

    // ── User ───────────────────────────────────────────────────────────────
    USERNAME_ALREADY_EXISTS ("VAL_010", "Username is already taken",                     HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS    ("VAL_011", "Email address is already registered",           HttpStatus.CONFLICT),
    PASSWORD_TOO_WEAK       ("VAL_012", "Password does not meet complexity requirements", HttpStatus.BAD_REQUEST),

    // ── General resource ───────────────────────────────────────────────────
    RESOURCE_NOT_FOUND      ("VAL_020", "Requested resource was not found",              HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS ("VAL_021", "Resource already exists",                       HttpStatus.CONFLICT);

    private final String     code;
    private final String     message;
    private final HttpStatus httpStatus;

    ValidationErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code       = code;
        this.message    = message;
        this.httpStatus = httpStatus;
    }

    @Override public String     code()    { return code; }
    @Override public String     message() { return message; }
    @Override public HttpStatus status()  { return httpStatus; }
}
