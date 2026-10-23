package com.erp.manufacturing.module.workorder.domain;

/**
 * G9c (FE Q20): QC progress of an APPROVED production receipt, independent of its document status. Derived from
 * the line totals, never stored.
 */
public enum ProductionQualityState {
    /** Approved, no decision yet. */
    NOT_STARTED,
    /** At least one decision, something still pending — even when part was rejected. */
    PARTIAL,
    /** Nothing pending, all accepted. */
    ACCEPTED,
    /** Nothing pending, all rejected. */
    REJECTED,
    /** Nothing pending, some accepted and some rejected. */
    MIXED
}
