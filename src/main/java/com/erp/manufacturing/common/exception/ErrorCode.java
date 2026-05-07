package com.erp.manufacturing.common.exception;

/**
 * Centralized error codes used in ApiResponse.code and exception handling.
 * Frontend should branch on these codes, NOT on HTTP status or message strings.
 */
public enum ErrorCode {

    // ── Success ────────────────────────────────────────────────────────────
    SUCCESS,

    // ── Auth ───────────────────────────────────────────────────────────────
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_INACTIVE,
    TOKEN_EXPIRED,
    TOKEN_REVOKED,
    TOKEN_MALFORMED,
    REFRESH_TOKEN_EXPIRED,
    SESSION_CONFLICT,

    // ── Authorization ──────────────────────────────────────────────────────
    ACCESS_DENIED,

    // ── Resource ───────────────────────────────────────────────────────────
    RESOURCE_NOT_FOUND,
    RESOURCE_ALREADY_EXISTS,

    // ── Business ───────────────────────────────────────────────────────────
    BUSINESS_RULE_VIOLATION,
    INSUFFICIENT_STOCK,
    BOM_CIRCULAR_REFERENCE,
    MRP_CALCULATION_ERROR,

    // ── Validation ─────────────────────────────────────────────────────────
    VALIDATION_FAILED,

    // ── Rate Limit ─────────────────────────────────────────────────────────
    RATE_LIMIT_EXCEEDED,

    // ── External / System ──────────────────────────────────────────────────
    EXTERNAL_SERVICE_ERROR,
    INTERNAL_SERVER_ERROR
}
