package com.erp.manufacturing.module.sales.dto;

/**
 * Whether each progress figure of a line is backed by data ({@code KNOWN}) or not ({@code UNKNOWN})
 * (G6a, D3). An {@code UNKNOWN} figure is {@code null} on the line — never a guessed {@code 0}.
 * {@code produced}: nothing traces production output to a sales order line.
 * {@code delivered}: no shipment/delivery document exists in the system yet (GĐ10).
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
        return new SalesOrderLineMetricsEvidence(KNOWN, KNOWN, UNKNOWN, KNOWN, UNKNOWN);
    }
}
