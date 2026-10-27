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
    /**
     * G2-4: a parent cannot be deactivated while it still has ACTIVE children — today a plant with an
     * ACTIVE warehouse. Refused rather than cascaded: deactivating the children is a separate decision
     * the user has to make (NEXT_PHASE_PLAN.md 9.2 D4). 422 like {@link #RESOURCE_INACTIVE}: it is
     * about master data, not about a document's status.
     */
    ACTIVE_CHILDREN_EXIST     ("ACTIVE_CHILDREN_EXIST",     "Record still has active children",              HttpStatus.UNPROCESSABLE_ENTITY),
    STATE_CONFLICT            ("STATE_CONFLICT",            "Action is not valid for the current state",     HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION   ("CONCURRENT_MODIFICATION",   "Record was modified by another request",        HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT      ("IDEMPOTENCY_CONFLICT",      "Idempotency key was reused with a different payload", HttpStatus.CONFLICT),
    /**
     * G4: the command would not change anything — a stock count equal to the current quantity, a lot
     * status change to the status it already has. Nothing is written. Split from
     * {@link #STATE_CONFLICT} so the client can say "nothing to record" instead of "refresh and retry".
     */
    NO_CHANGE                 ("NO_CHANGE",                 "The request would not change anything",         HttpStatus.CONFLICT),
    /**
     * G5: activating a BOM/routing version whose {@code effectiveFrom} is still in the future. Not a
     * plain STATE_CONFLICT because the remedy differs — wait, or change the date — and nothing is
     * ever activated automatically when the date arrives (FE §1.2).
     */
    EFFECTIVE_DATE_FUTURE     ("EFFECTIVE_DATE_FUTURE",     "The version is not effective yet",              HttpStatus.CONFLICT),
    /** G5 (FE Q18): a routing product already has a DRAFT version; open that one instead of a second. */
    OPEN_DRAFT_EXISTS         ("OPEN_DRAFT_EXISTS",         "An open draft already exists for this product", HttpStatus.CONFLICT),
    /**
     * G5: deactivating master data that ACTIVE records still use (a shift on an ACTIVE calendar, a
     * calendar on an ACTIVE work center, a work center on an ACTIVE routing or an open work order).
     * 422 like {@link #ACTIVE_CHILDREN_EXIST}: a master-data rule, not a document state conflict.
     */
    REFERENCE_IN_USE          ("REFERENCE_IN_USE",          "Record is still used by active records",        HttpStatus.UNPROCESSABLE_ENTITY),
    /** G5 (D10, reverses B90): a manual schedule would overlap the previous or next operation. */
    OPERATION_SEQUENCE_CONFLICT("OPERATION_SEQUENCE_CONFLICT", "Schedule overlaps the previous or next operation", HttpStatus.CONFLICT),
    /** G5: the calendar has no working time to place the request in (no calendar, or none within the horizon). */
    SCHEDULING_UNAVAILABLE    ("SCHEDULING_UNAVAILABLE",    "No working time is available to schedule this", HttpStatus.CONFLICT),
    /**
     * G6b: the sources a commercial planning run was computed from (sales order lines, held stock,
     * active BOMs and routings) changed since the run, or the client echoed another run's token.
     * The fix is a new run, not a retry — hence not STATE_CONFLICT.
     */
    PLANNING_SOURCE_STALE     ("PLANNING_SOURCE_STALE",     "Planning sources changed; run planning again before converting", HttpStatus.CONFLICT),
    /**
     * G7a (FE Q7): a draft purchase order that still holds planning quota keeps its warehouse,
     * items, quantities and line set; the request tried to change one of them. Not
     * STATE_CONFLICT: retrying after a reload fails the same way — the remedy is to cancel the
     * order (giving the quota back) or to change only the editable fields.
     */
    /**
     * G7b (FE Q11): a purchase order still has a DRAFT goods receipt, so it cannot be cancelled or
     * closed. Not STATE_CONFLICT: the order itself is in a state that allows it — the draft has to be
     * posted or cancelled first.
     */
    PENDING_GOODS_RECEIPT     ("PENDING_GOODS_RECEIPT",     "A draft goods receipt is still pending for this purchase order", HttpStatus.CONFLICT),
    /**
     * G10a (FE Q6): a known price on a confirmed sales order line is locked, zero included. Not STATE_CONFLICT:
     * reloading does not help — only a missing price can still be filled.
     */
    PRICE_LOCKED              ("PRICE_LOCKED",              "A known sales price cannot be changed once the order is confirmed", HttpStatus.CONFLICT),
    /**
     * G10b (FE Q5): the order has no known delivery baseline — confirmed before deliveries existed (D3), or without a
     * master customer (D4) — so nothing can be delivered or invoiced from it. Not STATE_CONFLICT: reloading does not
     * help; only a reconciliation outside this system can fill the gap.
     */
    SOURCE_UNKNOWN            ("SOURCE_UNKNOWN",            "This order has no known delivery baseline", HttpStatus.CONFLICT),
    /**
     * G10c (FE R13, A19): a sales order line has no price, so its delivery cannot be invoiced. Never billed as 0 — a
     * missing price is filled through {@code commercial-prices} by someone with the price authority.
     */
    PRICE_UNKNOWN             ("PRICE_UNKNOWN",             "A sales order line has no price yet", HttpStatus.CONFLICT),
    /**
     * G10c (FE R14, Q22, A22): the cumulative billing base (or the order's prices) changed since the invoice was
     * previewed or saved. Not CONCURRENT_MODIFICATION: the amounts themselves would change, so the user previews,
     * saves and confirms again — the server never changes an amount inside the old confirmation.
     */
    PRICE_STALE               ("PRICE_STALE",               "The invoice was priced on a billing base that has changed", HttpStatus.CONFLICT),
    /**
     * G11 (FE Q31): the sources of a work order's cost (version, issues, consumption, returns, executions, receipts,
     * QC, deliveries, output pool) changed since the cost was calculated. The user calculates again — reloading alone
     * would hand back the same stale run.
     */
    COST_SOURCE_STALE         ("COST_SOURCE_STALE",         "The cost sources changed; calculate the cost again", HttpStatus.CONFLICT),
    /** G11c (FE §3.6): an operational close condition is not met — the shop floor has to finish it first. */
    CLOSE_BLOCKED             ("CLOSE_BLOCKED",             "The work order cannot be closed yet", HttpStatus.CONFLICT),
    /** G11c: the baseline, a price, a rate, consumption or execution time is missing — no number is invented for it. */
    COST_DATA_INCOMPLETE      ("COST_DATA_INCOMPLETE",      "The cost data of the work order is incomplete", HttpStatus.CONFLICT),
    /** G11c (D3): the output pool holds stock or movements that are not this work order's own — no value is assigned. */
    VALUATION_UNRECONCILED    ("VALUATION_UNRECONCILED",    "The output value cannot be attributed to this work order", HttpStatus.CONFLICT),
    SOURCE_LOCKED             ("SOURCE_LOCKED",             "These fields come from planning and cannot be changed", HttpStatus.CONFLICT),
    /**
     * G8c (FE Q14): the first operation of a work order takes input only after material was issued to it. Not
     * STATE_CONFLICT: reloading does not help — a material issue has to be posted first.
     */
    MATERIAL_ISSUE_REQUIRED   ("MATERIAL_ISSUE_REQUIRED",   "Issue material to the work order before confirming operation input", HttpStatus.CONFLICT),
    /**
     * G8c (FE Q22): the work order had production reported before inputs and consumption were recorded, so the
     * server cannot work out the caps an operation command needs. Neither retrying nor reloading changes that.
     */
    PRODUCTION_INPUT_UNKNOWN  ("PRODUCTION_INPUT_UNKNOWN",  "Operation input and consumption of this work order were not recorded", HttpStatus.CONFLICT),

    // ── Inventory / Stock ──────────────────────────────────────────────────
    /** Spec §8.2 puts this at 409; F1 only aligned the wire code, F5 aligned the status (debt #9). */
    INSUFFICIENT_STOCK        ("INSUFFICIENT_AVAILABLE_STOCK", "Insufficient stock for this operation",      HttpStatus.CONFLICT),
    /**
     * G4: the command would break stock that is reserved (for a work order, or since G6a a sales order) — changing the status of a
     * lot with an ACTIVE reservation, or counting a position below its reserved quantity. The remedy
     * is to release the reservation at its source first, which is why this is not
     * {@link #INSUFFICIENT_STOCK}.
     */
    RESERVED_STOCK            ("RESERVED_STOCK",            "Stock is reserved and cannot be changed",       HttpStatus.CONFLICT),
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
    /**
     * G2-3: an item-warehouse setting claims a default role (supply/output) another warehouse of the
     * same item and plant already holds, and the caller sent {@code replaceDefaults=false}. Nothing is
     * written; the client should confirm and retry with {@code replaceDefaults=true}.
     */
    DEFAULT_CONFLICT          ("DEFAULT_CONFLICT",          "Default warehouse role is held by another warehouse", HttpStatus.CONFLICT),
    PLANNED_QUANTITY_EXCEEDED ("PLANNED_QUANTITY_EXCEEDED", "Quantity exceeds the planned limit",            HttpStatus.CONFLICT),
    /**
     * G3: a {@code READY} import batch could not be committed because the data it was validated
     * against changed (a code was taken, a UOM was deactivated). Nothing was created; the batch is now
     * {@code INVALID} and travels in {@code result} — the one error whose envelope carries a payload,
     * so the client can show the new row issues without losing its preview.
     */
    IMPORT_REVALIDATION_FAILED("IMPORT_REVALIDATION_FAILED", "Import batch is no longer valid; nothing was created", HttpStatus.CONFLICT),

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
