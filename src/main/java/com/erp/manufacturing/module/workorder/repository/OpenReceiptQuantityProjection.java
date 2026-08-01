package com.erp.manufacturing.module.workorder.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How much output a work order has already claimed with receipts that are still open
 * ({@code DRAFT} + {@code PENDING_APPROVAL}, invariant B16).
 *
 * <p>Exists so the receipt-candidate screen can show the same ceiling the query filtered on, without
 * asking per row (rule C14) — the same shape as {@link WorkOrderSupplyProjection}.
 */
public interface OpenReceiptQuantityProjection {

    UUID getWorkOrderId();

    BigDecimal getOpenQuantity();
}
