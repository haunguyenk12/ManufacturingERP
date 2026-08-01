package com.erp.manufacturing.module.workorder.domain;

public enum WipTransactionType {
    START,
    MATERIAL_ISSUED,
    /** The shop floor produced good output (Production Execution). */
    OUTPUT_COMPLETED,
    /**
     * An approved production receipt moved output into stock. Split from {@code OUTPUT_COMPLETED}
     * in F5: producing and warehousing are no longer the same event (CLAUDE.md §0.5).
     */
    OUTPUT_RECEIPTED,
    SCRAP_REPORTED,
    REWORK_REPORTED
}
