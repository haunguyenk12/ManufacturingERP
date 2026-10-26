package com.erp.manufacturing.module.sales.dto;

/**
 * Whether each progress figure of a line is backed by data ({@code KNOWN}) or not ({@code UNKNOWN})
 * (G6a, D3). An {@code UNKNOWN} figure is {@code null} on the line — never a guessed {@code 0}.
 * {@code produced}: nothing traces production output to a sales order line.
 * {@code delivered}: G10b — summed from POSTED deliveries; {@code UNKNOWN} only on orders whose delivery baseline is
 * unknown (confirmed before V86, D3).
 */
public record SalesOrderLineMetricsEvidence(
        String heldStock,
        String workOrderCovered,
        String produced,
        String qcAccepted,
        String delivered
) {
    public static final String KNOWN = "KNOWN";
    public static final String UNKNOWN = "UNKNOWN";

    public static SalesOrderLineMetricsEvidence current() {
        return of(false);
    }

    /** G10b: {@code delivered} is known once deliveries exist and the order's baseline is known (D3). */
    public static SalesOrderLineMetricsEvidence of(boolean deliveredKnown) {
        return new SalesOrderLineMetricsEvidence(KNOWN, KNOWN, UNKNOWN, KNOWN, deliveredKnown ? KNOWN : UNKNOWN);
    }
}
