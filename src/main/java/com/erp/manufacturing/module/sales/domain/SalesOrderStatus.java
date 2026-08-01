package com.erp.manufacturing.module.sales.domain;

/**
 * Sales order lifecycle per the FE handoff spec §1 / §7.1.
 *
 * <p>{@code F3} implements the front half only — {@code DRAFT} → {@code CONFIRMED} → {@code CANCELLED}.
 * {@code IN_PRODUCTION}, {@code PARTIALLY_FULFILLED} and {@code FULFILLED} are reachable states of
 * the finished flow but are only ever <b>entered</b> by fulfillment allocation, which is {@code F6}.
 * They are declared (and accepted by the {@code V28} CHECK constraint) so the eligibility filter of
 * the planning-demand query can already name them.
 */
public enum SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    IN_PRODUCTION,
    PARTIALLY_FULFILLED,
    FULFILLED,
    CANCELLED
}
