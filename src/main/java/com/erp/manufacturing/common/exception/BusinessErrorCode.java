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
    /**
     * Catch-all for "the data you sent is wrong" once the three named cases below are excluded:
     * a wrong {@code ItemType}, a lot on a non-lot-tracked item, a due date before its order date,
     * a break outside its shift. It is still the right answer for those — but it used to be the
     * answer for <em>every</em> 422 in the system, which is what EH-2 broke up.
     */
    OPERATION_NOT_ALLOWED     ("OPERATION_NOT_ALLOWED",     "Operation is not allowed in the current state", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * EH-2: a master-data record referenced by this request is {@code INACTIVE} — the company, plant,
     * warehouse, item, supplier, work center, work calendar, role, permission, scope or import profile
     * behind it has been retired. Split out of {@link #OPERATION_NOT_ALLOWED} because the caller's
     * remedy is specific and different: reactivate the record (or pick a live one), not fix the
     * payload. Also covers activating a child while its parent is inactive (§0.39).
     */
    RESOURCE_INACTIVE         ("RESOURCE_INACTIVE",         "A referenced record is inactive",               HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * EH-2: two records in this request belong to different owners — an item from another company, a
     * warehouse from another plant, a lot or serial from another item, a routing operation pointing at
     * a work center in another plant. The remedy is to change the selection, which is why this is not
     * the same answer as {@link #RESOURCE_INACTIVE} or a malformed payload.
     */
    RESOURCE_SCOPE_MISMATCH   ("RESOURCE_SCOPE_MISMATCH",   "Records belong to different scopes",            HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * EH-2: a document cannot be activated or applied because it has no child rows yet — a BOM with no
     * component lines, a routing with no operations, an import run with no valid rows, a non-global
     * access scope with no resources.
     *
     * <p>🔴 This stays <b>422</b>, not {@link #STATE_CONFLICT}. It reads as a state check but it is
     * not: the document's {@code status} is fine, its <em>content</em> is incomplete. The distinction
     * is set by {@code RoutingService.activate}, which has separated the two branches since F4, and is
     * called out in {@code .claude/rules/error-handling.md} §5.3 as the boundary people get wrong.
     */
    DOCUMENT_HAS_NO_LINES     ("DOCUMENT_HAS_NO_LINES",     "Document has no lines yet",                     HttpStatus.UNPROCESSABLE_ENTITY),
    STATE_CONFLICT            ("STATE_CONFLICT",            "Action is not valid for the current state",     HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION   ("CONCURRENT_MODIFICATION",   "Record was modified by another request",        HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT      ("IDEMPOTENCY_CONFLICT",      "Idempotency key was reused with a different payload", HttpStatus.CONFLICT),

    // ── Inventory / Stock ──────────────────────────────────────────────────
    /** Spec §8.2 puts this at 409; F1 only aligned the wire code, F5 aligned the status (debt #9). */
    INSUFFICIENT_STOCK        ("INSUFFICIENT_AVAILABLE_STOCK", "Insufficient stock for this operation",      HttpStatus.CONFLICT),
    NEGATIVE_QUANTITY         ("NEGATIVE_QUANTITY",         "Quantity cannot be negative",                   HttpStatus.UNPROCESSABLE_ENTITY),
    /** EH-5: no throw site yet. Kept because {@code docs/api-guide-for-frontend.md} already lists it
     *  for the frontend; removing it would break a published contract before anything replaces it. */
    ITEM_ALREADY_ISSUED       ("ITEM_ALREADY_ISSUED",       "Item has already been issued",                  HttpStatus.CONFLICT),
    LOT_NOT_ELIGIBLE          ("LOT_NOT_ELIGIBLE",          "Lot status does not allow this operation",      HttpStatus.CONFLICT),
    LOT_CODE_ALREADY_EXISTS   ("LOT_CODE_ALREADY_EXISTS",   "Lot code already exists for this item",        HttpStatus.CONFLICT),
    LOT_REUSE_NOT_ALLOWED     ("LOT_REUSE_NOT_ALLOWED",     "The selected lot cannot be reused",            HttpStatus.CONFLICT),
    LOT_ITEM_MISMATCH         ("LOT_ITEM_MISMATCH",         "Lot belongs to a different item",              HttpStatus.CONFLICT),
    /** EH-5: no throw site yet. Reserved by {@code BE_SYSTEM_ISSUES_RESOLUTION_PLAN_2026-08-19.md}
     *  as part of the stable error contract phase BE-4 will emit. */
    LOT_WAREHOUSE_CONFLICT    ("LOT_WAREHOUSE_CONFLICT",    "Lot cannot be used in this warehouse",         HttpStatus.CONFLICT),
    OUTPUT_LOT_NOT_POSTED     ("OUTPUT_LOT_NOT_POSTED",     "Receipt output lot has not been posted",       HttpStatus.CONFLICT),
    /** EH-5: no throw site yet. Reserved by phase BE-4, same plan as {@link #LOT_WAREHOUSE_CONFLICT}. */
    RECEIPT_STATE_CONFLICT    ("RECEIPT_STATE_CONFLICT",    "Receipt state does not allow this operation",  HttpStatus.CONFLICT),
    SERIAL_NOT_ELIGIBLE       ("SERIAL_NOT_ELIGIBLE",       "Serial status does not allow this operation",   HttpStatus.CONFLICT),

    // ── BOM / Manufacturing ────────────────────────────────────────────────
    BOM_CIRCULAR_REFERENCE    ("BOM_CIRCULAR_REFERENCE",    "Circular reference detected in Bill of Materials", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Spec §8.1 planning message code: a MAKE item without an ACTIVE routing cannot become a work
     *  order. 409 follows the §8.2 "action not valid for the current master data state" family. */
    MISSING_ROUTING           ("MISSING_ROUTING",           "No active routing for this item",               HttpStatus.CONFLICT),
    /** Sibling of {@link #MISSING_ROUTING}. Until F5 this surfaced as a 404 from the BOM lookup,
     *  which spec §8.1 treats as the same class of planning block (debt #14). */
    MISSING_BOM               ("MISSING_BOM",               "No active BOM for this item",                   HttpStatus.CONFLICT),
    /** EH-5: no throw site yet. Kept for the same reason as {@link #ITEM_ALREADY_ISSUED}. */
    PRODUCTION_ORDER_CLOSED   ("PRODUCTION_ORDER_CLOSED",   "Production order is already closed",            HttpStatus.CONFLICT),
    RESERVATION_EXCEEDED      ("RESERVATION_EXCEEDED",      "Quantity exceeds the remaining reservation",    HttpStatus.CONFLICT),
    /** EH-5: no throw site yet. Reserved by phase BE-2 of
     *  {@code BE_SYSTEM_ISSUES_RESOLUTION_PLAN_2026-08-19.md} (over-BOM issue approval). */
    BOM_REQUIREMENT_EXCEEDED  ("BOM_REQUIREMENT_EXCEEDED",  "Quantity exceeds the BOM requirement",          HttpStatus.CONFLICT),
    OVER_BOM_APPROVAL_REQUIRED("OVER_BOM_APPROVAL_REQUIRED", "Over-BOM issue requires manager approval",      HttpStatus.CONFLICT),
    MISSING_WAREHOUSE_POLICY  ("MISSING_WAREHOUSE_POLICY",  "No warehouse policy resolves this requirement", HttpStatus.CONFLICT),
    AMBIGUOUS_WAREHOUSE_POLICY("AMBIGUOUS_WAREHOUSE_POLICY", "Multiple warehouses match without a default",   HttpStatus.CONFLICT),
    PLANNED_QUANTITY_EXCEEDED ("PLANNED_QUANTITY_EXCEEDED", "Quantity exceeds the planned limit",            HttpStatus.CONFLICT),

    // ── System ─────────────────────────────────────────────────────────────
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
