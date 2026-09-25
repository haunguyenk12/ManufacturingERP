package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Error codes for input validation failures.
 * The wire value is the enum constant name so clients branch on a stable, readable code
 * (see {@code .claude/rules/error-handling.md} §5.3).
 */
public enum ValidationErrorCode implements ErrorCode {

    // ── Generic ────────────────────────────────────────────────────────────
    INVALID_INPUT            ("VALIDATION_ERROR",          "Invalid input data",                            HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_FIELD   ("MISSING_REQUIRED_FIELD",    "Required field is missing",                     HttpStatus.BAD_REQUEST),
    FIELD_TOO_LONG           ("FIELD_TOO_LONG",            "Field value exceeds maximum length",            HttpStatus.BAD_REQUEST),
    FIELD_FORMAT_INVALID     ("FIELD_FORMAT_INVALID",      "Field value has an invalid format",             HttpStatus.BAD_REQUEST),

    // ── User ───────────────────────────────────────────────────────────────
    USERNAME_ALREADY_EXISTS  ("USERNAME_ALREADY_EXISTS",   "Username is already taken",                     HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS     ("EMAIL_ALREADY_EXISTS",      "Email address is already registered",           HttpStatus.CONFLICT),

    // ── Manufacturing input ────────────────────────────────────────────────
    LOT_REQUIRED             ("LOT_REQUIRED",              "Lot number is required for a lot-tracked item", HttpStatus.BAD_REQUEST),
    SERIAL_REQUIRED          ("SERIAL_REQUIRED",           "Serial number is required for a serial-tracked item", HttpStatus.BAD_REQUEST),
    APPROVAL_REASON_REQUIRED ("APPROVAL_REASON_REQUIRED",  "A reason is required for this decision",        HttpStatus.BAD_REQUEST),

    // ── General resource ───────────────────────────────────────────────────
    RESOURCE_NOT_FOUND       ("ENTITY_NOT_FOUND",          "Requested resource was not found",              HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS  ("RESOURCE_ALREADY_EXISTS",   "Resource already exists",                       HttpStatus.CONFLICT);

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
