package com.erp.manufacturing.module.dataimport.domain;

/**
 * Which of the two import paths created a run. They share {@code import_runs}/{@code import_rows}
 * but never see each other's rows: every query of either path filters on this column.
 */
public enum ImportRunKind {

    /** {@code /v1/import-runs}: profile-mapped columns, per-row apply, {@code PARTIALLY_APPLIED} allowed. */
    MAPPED_PROFILE,

    /**
     * {@code /v1/data-import/batches} (G3): the {@code omniplant-import-v1} template, validated in one
     * call and committed all-or-nothing. Lifecycle {@code READY|INVALID → COMMITTED}.
     */
    FIXED_TEMPLATE
}
