package com.erp.manufacturing.module.dataimport.domain;

/**
 * Machine-readable reason a single cell (or row) was rejected.
 *
 * <p>Separate from {@code ErrorCode}: those describe the outcome of an HTTP call, these describe one
 * row inside a call that succeeded. An import with a thousand bad rows is still a 200 — the report is
 * the payload, not the failure — so these codes never map to an HTTP status.
 *
 * <p>The frontend groups and filters on this, which is why it exists at all; a message string alone
 * would force it to parse prose.
 */
public enum ImportErrorCode {

    /** A field the target requires had no value and the mapping supplied no default. */
    REQUIRED_MISSING,

    /** Value is longer than the column allows. */
    TOO_LONG,

    /** Value is not one of the allowed values for an enum field. */
    NOT_ALLOWED_VALUE,

    /** Value does not match the field's expected shape (boolean word, code pattern, ...). */
    INVALID_FORMAT,

    /** Two rows in this same file claim the same business key. */
    DUPLICATE_IN_FILE,

    /** The business key already exists in the database. */
    ALREADY_EXISTS,

    /** A referenced code (item, warehouse, ...) does not resolve to an existing record. */
    REFERENCE_NOT_FOUND,

    /** Combination of values the target rejects (e.g. lot-tracked and serial-tracked at once). */
    RULE_VIOLATION,

    /** The write failed for a reason only surfacing at apply time. */
    APPLY_FAILED
}
