package com.erp.manufacturing.module.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One lot's balance in one warehouse (C2-2) — list rows and the status-change response share this
 * shape.
 *
 * <p>{@code receivedAt} is the authoritative moment the lot entered stock, read straight from
 * {@link com.erp.manufacturing.module.inventory.domain.InventoryLot#getReceivedAt()}. Clients must
 * label "date received" with this field and <b>not</b> with {@code createdAt}: the two coincide only
 * for a lot whose row was inserted by its own first receipt, and drift apart for any lot restocked
 * later or created by a different flow. {@code createdAt}/{@code updatedAt} stay what they always
 * are — row audit timestamps, not business dates.
 *
 * <p>{@code manufactureDate} is a <b>legacy alias carrying the same value as {@code receivedAt}</b>,
 * kept only so existing clients do not break. It is misnamed: this system stores no separate
 * manufacture date. Do not surface it as one — read {@code receivedAt} instead.
 *
 * <p>{@code sourceMovementType}/{@code sourceReferenceType}/{@code sourceReferenceId}/{@code sourceAt}
 * are derived from the lot's earliest {@code RECEIVE} movement and may all be {@code null} if none
 * is found. {@code sourceAt} is the ledger timestamp of that movement, which is the closest thing to
 * "when did this arrive and from where"; {@code receivedAt} is the lot's own stamp.
 */
public record InventoryLotResponse(
        UUID lotId,
        UUID itemId,
        String itemCode,
        String itemName,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        String lotCode,
        String status,
        BigDecimal onHandQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        Instant receivedAt,
        Instant manufactureDate,
        Instant expiresAt,
        String sourceMovementType,
        String sourceReferenceType,
        String sourceReferenceId,
        Instant sourceAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
