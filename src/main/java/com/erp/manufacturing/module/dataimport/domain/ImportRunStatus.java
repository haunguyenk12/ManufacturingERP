package com.erp.manufacturing.module.dataimport.domain;

/**
 * Lifecycle of one uploaded spreadsheet.
 *
 * <pre>
 *   PARSED ──validate──▶ VALIDATED ──apply──▶ APPLIED
 *      │                     │                PARTIALLY_APPLIED
 *      │                     │                FAILED
 *      └──────cancel─────────┴──────────────▶ CANCELLED
 * </pre>
 *
 * <p>Upload never writes master data — it only fills the staging table — which is why {@code PARSED}
 * and {@code VALIDATED} are distinct: a user whose headers did not match re-points the run at another
 * profile and validates again, without re-uploading the file.
 */
public enum ImportRunStatus {

    /** Workbook is being read into staging. */
    PARSING,

    /** File read into {@code import_rows}; no mapping applied yet. */
    PARSED,

    /** Mapping and batch resolution are in progress. */
    VALIDATING,

    /** Mapping + transforms + checks done; every row carries VALID or ERROR. */
    VALIDATED,

    /** Valid rows are being written in independent transactions. */
    APPLYING,

    /** Apply finished and every valid row was written. */
    APPLIED,

    /** Apply finished, but at least one row failed while others were written. */
    PARTIALLY_APPLIED,

    /** Apply aborted before touching rows (or every row failed). */
    FAILED,

    /** Abandoned by the user before apply. */
    CANCELLED;

    public boolean isTerminal() {
        return this == APPLIED || this == PARTIALLY_APPLIED || this == FAILED || this == CANCELLED;
    }
}
