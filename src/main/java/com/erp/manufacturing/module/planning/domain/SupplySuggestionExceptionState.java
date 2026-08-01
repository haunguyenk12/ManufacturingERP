package com.erp.manufacturing.module.planning.domain;

/**
 * Whether a supply suggestion can be acted on (spec §2.4 {@code exceptionState}).
 * {@link #BLOCKED} means master data is missing, so the suggestion must never become a work order.
 */
public enum SupplySuggestionExceptionState {
    READY,
    WARNING,
    BLOCKED
}
