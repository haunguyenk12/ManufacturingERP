package com.erp.manufacturing.module.sales.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code fulfilledQuantity}/{@code openQuantity} keep their legacy meaning (FE Q26/Q27):
 * {@code fulfilled} = output QC released to {@code AVAILABLE} for this line, {@code open} =
 * ordered − fulfilled. Neither means "delivered to the customer".
 *
 * <p>G6a progress figures, appended (additive on the wire):
 * {@code heldStockQuantity} — stock held for this line, still ACTIVE;
 * {@code workOrderCoveredQuantity} — work-order allocations not yet QC-released, work order not
 * cancelled/closed ({@code DRAFT} included, FE Q2);
 * {@code producedQuantity} — always {@code null} (no per-line production trace);
 * {@code qcAcceptedQuantity} — same number as {@code fulfilledQuantity};
 * {@code deliveredQuantity} — always {@code null} (no shipment document yet);
 * {@code uncoveredQuantity} = max(0, ordered − qcAccepted − heldStock − workOrderCovered).
 * The figures are filled on the detail response; on rows without metrics they are {@code null}.
 *
 * <p>G10a: {@code commercial} — the line's price — is <b>absent</b> from the JSON unless the caller may see sales
 * prices (D6). Never {@code null} on the wire: {@code default-property-inclusion: always} would print a null.
 */
public record SalesOrderLineResponse(
        UUID salesOrderLineId,
        Integer lineNo,
        UUID itemId,
        String itemSku,
        String itemName,
        String uom,
        BigDecimal orderedQuantity,
        BigDecimal fulfilledQuantity,
        BigDecimal openQuantity,
        LocalDate dueDate,
        BigDecimal heldStockQuantity,
        BigDecimal workOrderCoveredQuantity,
        BigDecimal producedQuantity,
        BigDecimal qcAcceptedQuantity,
        BigDecimal deliveredQuantity,
        BigDecimal uncoveredQuantity,
        SalesOrderLineMetricsEvidence metricsEvidence,
        @JsonInclude(JsonInclude.Include.NON_NULL) SalesOrderLineCommercialResponse commercial
) {
    /** Shape before G10a, no money. */
    public SalesOrderLineResponse(UUID salesOrderLineId, Integer lineNo, UUID itemId, String itemSku, String itemName,
                                  String uom, BigDecimal orderedQuantity, BigDecimal fulfilledQuantity,
                                  BigDecimal openQuantity, LocalDate dueDate, BigDecimal heldStockQuantity,
                                  BigDecimal workOrderCoveredQuantity, BigDecimal producedQuantity,
                                  BigDecimal qcAcceptedQuantity, BigDecimal deliveredQuantity,
                                  BigDecimal uncoveredQuantity, SalesOrderLineMetricsEvidence metricsEvidence) {
        this(salesOrderLineId, lineNo, itemId, itemSku, itemName, uom, orderedQuantity, fulfilledQuantity,
                openQuantity, dueDate, heldStockQuantity, workOrderCoveredQuantity, producedQuantity,
                qcAcceptedQuantity, deliveredQuantity, uncoveredQuantity, metricsEvidence, null);
    }
}
