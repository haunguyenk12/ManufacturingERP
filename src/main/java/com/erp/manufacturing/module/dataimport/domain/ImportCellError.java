package com.erp.manufacturing.module.dataimport.domain;

/**
 * One problem found in one row, stored as JSON in {@code import_rows.errors} and returned verbatim
 * to the client.
 *
 * <p>This is the piece the existing response envelope could not express: {@code ApiResponse.errors}
 * is a list of {@code (field, message)} with nowhere to put a row number, and it only travels on a
 * failed call. An import needs per-row detail on a call that succeeded.
 *
 * @param column      source header the value came from; {@code null} for a row-level problem
 * @param targetField system field it was mapped to; {@code null} when the mapping itself is the issue
 * @param code        machine-readable reason, for filtering and grouping
 * @param message     human-readable explanation naming the offending value
 */
public record ImportCellError(String column, String targetField, ImportErrorCode code, String message) {

    public static ImportCellError of(String column, String targetField, ImportErrorCode code, String message) {
        return new ImportCellError(column, targetField, code, message);
    }

    /** A problem that belongs to the whole row rather than to one cell. */
    public static ImportCellError row(ImportErrorCode code, String message) {
        return new ImportCellError(null, null, code, message);
    }
}
