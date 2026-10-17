package com.erp.manufacturing.module.sales.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * G6a fields are appended (additive on the wire): the customer link ({@code customerId} is
 * {@code null} on orders created before G6 or through the legacy path), the cancel reason, a line
 * summary for the list ({@code earliestDueDate}/{@code latestDueDate}/{@code lineCount}, filled on
 * list and detail), and {@code linkedDocuments} (detail only, empty on the list).
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
        List<SalesOrderLinkedDocumentResponse> linkedDocuments
) {}
