package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Error codes for domain / business rule violations.
 * Format: {@code BIZ_XXX}
 */
public enum BusinessErrorCode implements ErrorCode {

    // ── Generic ────────────────────────────────────────────────────────────
    BUSINESS_RULE_VIOLATION ("BIZ_001", "Business rule violated",                        HttpStatus.UNPROCESSABLE_ENTITY),
    OPERATION_NOT_ALLOWED   ("BIZ_002", "Operation is not allowed in the current state", HttpStatus.UNPROCESSABLE_ENTITY),

    // ── Inventory / Stock ──────────────────────────────────────────────────
    INSUFFICIENT_STOCK      ("BIZ_010", "Insufficient stock for this operation",         HttpStatus.UNPROCESSABLE_ENTITY),
    NEGATIVE_QUANTITY       ("BIZ_011", "Quantity cannot be negative",                   HttpStatus.UNPROCESSABLE_ENTITY),
    ITEM_ALREADY_ISSUED     ("BIZ_012", "Item has already been issued",                  HttpStatus.CONFLICT),

    // ── BOM / Manufacturing ────────────────────────────────────────────────
    BOM_CIRCULAR_REFERENCE  ("BIZ_020", "Circular reference detected in Bill of Materials", HttpStatus.UNPROCESSABLE_ENTITY),
    MRP_CALCULATION_ERROR   ("BIZ_021", "MRP calculation failed",                        HttpStatus.INTERNAL_SERVER_ERROR),
    PRODUCTION_ORDER_CLOSED ("BIZ_022", "Production order is already closed",            HttpStatus.CONFLICT),

    // ── System ─────────────────────────────────────────────────────────────
    EXTERNAL_SERVICE_ERROR  ("BIZ_090", "External service returned an error",            HttpStatus.BAD_GATEWAY),
    INTERNAL_SERVER_ERROR   ("BIZ_099", "An unexpected internal error occurred",         HttpStatus.INTERNAL_SERVER_ERROR),

    // ── Rate Limit ─────────────────────────────────────────────────────────
    RATE_LIMIT_EXCEEDED     ("BIZ_100", "Too many requests, please slow down",           HttpStatus.TOO_MANY_REQUESTS);

    private final String     code;
    private final String     message;
    private final HttpStatus httpStatus;

    BusinessErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code       = code;
        this.message    = message;
        this.httpStatus = httpStatus;
    }

    @Override public String     code()    { return code; }
    @Override public String     message() { return message; }
    @Override public HttpStatus status()  { return httpStatus; }
}
