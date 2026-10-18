package com.erp.manufacturing.module.planning.domain;

/**
 * {@code STANDARD} is the run that existed before G6b ({@code POST /planning-runs}, demand selected
 * by planning demand id or by horizon). {@code COMMERCIAL} plans sales order lines' uncovered
 * quantity, records a source version, and is the only kind the conversion ledger accepts.
 */
public enum MrpRunKind {
    STANDARD,
    COMMERCIAL
}
