package com.erp.manufacturing.module.sales.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * G6a fields are appended (additive on the wire): the customer link ({@code customerId} is
 * {@code null} on orders created before G6 or through the legacy path), the cancel reason, a line
 * summary for the list ({@code earliestDueDate}/{@code latestDueDate}/{@code lineCount}, filled on
 * list and detail), and {@code linkedDocuments} (detail only, empty on the list).
 *
 * <p>G10a: {@code deliveryBaselineKnown} (D3) — {@code false} on orders already past DRAFT when V86 ran, which can
 * never be delivered or invoiced; {@code deliveryBaselineUnknownReason} is {@code null} when known. {@code commercial}
 * is absent unless the caller may see sales prices (D6).
 */
public record SalesOrderResponse(
        UUID salesOrderId,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        String orderNo,
        String customerName,
        LocalDate orderDate,
        String status,
        String note,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<SalesOrderLineResponse> lines,
        UUID customerId,
        String customerCode,
        String customerReference,
        String cancelReason,
        LocalDate earliestDueDate,
        LocalDate latestDueDate,
        Integer lineCount,
        List<SalesOrderLinkedDocumentResponse> linkedDocuments,
        boolean deliveryBaselineKnown,
        String deliveryBaselineUnknownReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) SalesOrderCommercialResponse commercial
) {
    public static final String LEGACY_ORDER = "LEGACY_ORDER";

    /** Shape before G10a: baseline known, no money. */
    public SalesOrderResponse(UUID salesOrderId, UUID companyId, String companyCode, UUID plantId, String plantCode,
                              String orderNo, String customerName, LocalDate orderDate, String status, String note,
                              Instant createdAt, Instant updatedAt, Long version, List<SalesOrderLineResponse> lines,
                              UUID customerId, String customerCode, String customerReference, String cancelReason,
                              LocalDate earliestDueDate, LocalDate latestDueDate, Integer lineCount,
                              List<SalesOrderLinkedDocumentResponse> linkedDocuments) {
        this(salesOrderId, companyId, companyCode, plantId, plantCode, orderNo, customerName, orderDate, status, note,
                createdAt, updatedAt, version, lines, customerId, customerCode, customerReference, cancelReason,
                earliestDueDate, latestDueDate, lineCount, linkedDocuments, true, null, null);
    }
}
