package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Error codes for domain / business rule violations.
 * The wire value is the enum constant name so clients branch on a stable, readable code
 * (see {@code .claude/rules/error-handling.md} §5.3).
 */
public enum BusinessErrorCode implements ErrorCode {

    // ── Generic ────────────────────────────────────────────────────────────
    BUSINESS_RULE_VIOLATION   ("BUSINESS_RULE_VIOLATION",   "Business rule violated",                        HttpStatus.UNPROCESSABLE_ENTITY),
    OPERATION_NOT_ALLOWED     ("OPERATION_NOT_ALLOWED",     "Operation is not allowed in the current state", HttpStatus.UNPROCESSABLE_ENTITY),
    STATE_CONFLICT            ("STATE_CONFLICT",            "Action is not valid for the current state",     HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION   ("CONCURRENT_MODIFICATION",   "Record was modified by another request",        HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT      ("IDEMPOTENCY_CONFLICT",      "Idempotency key was reused with a different payload", HttpStatus.CONFLICT),

    // ── Inventory / Stock ──────────────────────────────────────────────────
    /** Spec §8.2 puts this at 409; F1 only aligned the wire code, F5 aligned the status (debt #9). */
    INSUFFICIENT_STOCK        ("INSUFFICIENT_AVAILABLE_STOCK", "Insufficient stock for this operation",      HttpStatus.CONFLICT),
    NEGATIVE_QUANTITY         ("NEGATIVE_QUANTITY",         "Quantity cannot be negative",                   HttpStatus.UNPROCESSABLE_ENTITY),
    ITEM_ALREADY_ISSUED       ("ITEM_ALREADY_ISSUED",       "Item has already been issued",                  HttpStatus.CONFLICT),
    LOT_NOT_ELIGIBLE          ("LOT_NOT_ELIGIBLE",          "Lot status does not allow this operation",      HttpStatus.CONFLICT),
    SERIAL_NOT_ELIGIBLE       ("SERIAL_NOT_ELIGIBLE",       "Serial status does not allow this operation",   HttpStatus.CONFLICT),

    // ── BOM / Manufacturing ────────────────────────────────────────────────
    BOM_CIRCULAR_REFERENCE    ("BOM_CIRCULAR_REFERENCE",    "Circular reference detected in Bill of Materials", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Spec §8.1 planning message code: a MAKE item without an ACTIVE routing cannot become a work
     *  order. 409 follows the §8.2 "action not valid for the current master data state" family. */
    MISSING_ROUTING           ("MISSING_ROUTING",           "No active routing for this item",               HttpStatus.CONFLICT),
    /** Sibling of {@link #MISSING_ROUTING}. Until F5 this surfaced as a 404 from the BOM lookup,
     *  which spec §8.1 treats as the same class of planning block (debt #14). */
    MISSING_BOM               ("MISSING_BOM",               "No active BOM for this item",                   HttpStatus.CONFLICT),
    MRP_CALCULATION_ERROR     ("MRP_CALCULATION_ERROR",     "MRP calculation failed",                        HttpStatus.INTERNAL_SERVER_ERROR),
    PRODUCTION_ORDER_CLOSED   ("PRODUCTION_ORDER_CLOSED",   "Production order is already closed",            HttpStatus.CONFLICT),
    RESERVATION_EXCEEDED      ("RESERVATION_EXCEEDED",      "Quantity exceeds the remaining reservation",    HttpStatus.CONFLICT),
    PLANNED_QUANTITY_EXCEEDED ("PLANNED_QUANTITY_EXCEEDED", "Quantity exceeds the planned limit",            HttpStatus.CONFLICT),

    // ── System ─────────────────────────────────────────────────────────────
    EXTERNAL_SERVICE_ERROR    ("EXTERNAL_SERVICE_ERROR",    "External service returned an error",            HttpStatus.BAD_GATEWAY),
    INTERNAL_SERVER_ERROR     ("INTERNAL_SERVER_ERROR",     "An unexpected internal error occurred",         HttpStatus.INTERNAL_SERVER_ERROR),

    // ── Rate Limit ─────────────────────────────────────────────────────────
    RATE_LIMIT_EXCEEDED       ("RATE_LIMIT_EXCEEDED",       "Too many requests, please slow down",           HttpStatus.TOO_MANY_REQUESTS);

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
