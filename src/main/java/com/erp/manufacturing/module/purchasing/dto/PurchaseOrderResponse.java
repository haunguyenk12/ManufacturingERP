package com.erp.manufacturing.module.purchasing.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID purchaseOrderId,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        UUID warehouseId,
        String warehouseCode,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        String purchaseOrderNo,
        String status,
        LocalDate orderDate,
        LocalDate expectedDate,
        UUID sourceRequisitionId,
        String note,
        Instant createdAt,
        Instant updatedAt,
        List<PurchaseOrderLineResponse> lines,
        // ── G7a, additive ──
        /** Same value as {@code purchaseOrderNo} (FE name). */
        String code,
        Long version,
        String warehouseName,
        String reference,
        /** The single currency of the lines, or {@code null} when none is set. New drafts are VND. */
        String currencyCode,
        /** The order still holds planning quota: warehouse, items, quantities and lines are locked. */
        boolean sourceLocked,
        /** Cancel or close reason; {@code null} otherwise. */
        String reason,
        Instant confirmedAt,
        Instant cancelledAt,
        Instant closedAt,
        UUID createdBy,
        /** Detail only; empty on list rows. */
        List<PurchaseOrderHistoryResponse> history
) {

    /** Pre-G7a arity, kept so internal callers and tests that build the legacy shape still compile. */
    public PurchaseOrderResponse(UUID purchaseOrderId, UUID companyId, String companyCode, UUID plantId,
                                 String plantCode, UUID warehouseId, String warehouseCode, UUID supplierId,
                                 String supplierCode, String supplierName, String purchaseOrderNo, String status,
                                 LocalDate orderDate, LocalDate expectedDate, UUID sourceRequisitionId, String note,
                                 Instant createdAt, Instant updatedAt, List<PurchaseOrderLineResponse> lines) {
        this(purchaseOrderId, companyId, companyCode, plantId, plantCode, warehouseId, warehouseCode, supplierId,
                supplierCode, supplierName, purchaseOrderNo, status, orderDate, expectedDate, sourceRequisitionId, note,
                createdAt, updatedAt, lines, purchaseOrderNo, null, null, null, null, false, null, null, null, null,
                null, List.of());
    }
}
