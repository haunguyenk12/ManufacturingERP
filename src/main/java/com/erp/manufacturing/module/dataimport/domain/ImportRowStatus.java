package com.erp.manufacturing.module.dataimport.domain;

/** Outcome of a single spreadsheet row. */
public enum ImportRowStatus {

    /** Parsed into staging, not validated yet. */
    PENDING,

    /** Passed validation; eligible for apply. */
    VALID,

    /** Failed validation; {@code errors} says which cell and why. Never applied. */
    ERROR,

    /** Written to master data; {@code createdEntityId} points at the result. */
    APPLIED,

    /** Was VALID, but the write itself failed (e.g. a concurrent create took the code). */
    FAILED,

    /** Deliberately ignored by a grouped target or a later resume operation. */
    SKIPPED
}
