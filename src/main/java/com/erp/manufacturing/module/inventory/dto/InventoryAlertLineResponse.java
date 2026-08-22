package com.erp.manufacturing.module.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One alert row per {@code (item, warehouse)} — safety stock and reorder point are configured per
 * warehouse, so the same item in two warehouses is two independent rows with two independent
 * statuses.
 */
public record InventoryAlertLineResponse(
        UUID settingId,
        UUID itemId,
        String itemCode,
        String itemName,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        @Schema(description = "Configured safety stock for this item in this warehouse")
        BigDecimal safetyStock,
        @Schema(description = "Configured reorder point for this item in this warehouse")
        BigDecimal reorderPoint,
        Integer leadTimeDays,
        @Schema(description = "onHandQuantity − reservedQuantity − qualityHoldQuantity. Never negative.")
        BigDecimal availableQuantity,
        @Schema(description = "OK | LOW_STOCK | REORDER_NEEDED. Mutually exclusive; "
                + "REORDER_NEEDED outranks LOW_STOCK.")
        String status,
        @Schema(description = "Display unit of the item (items.unit — free text, not yet an FK to the "
                + "UOM master)", example = "PCS")
        String uomCode,
        @Schema(description = "Eligible on-hand: stock physically in the warehouse, excluding lots that "
                + "are not AVAILABLE")
        BigDecimal onHandQuantity,
        @Schema(description = "Part of the eligible on-hand already reserved for work orders")
        BigDecimal reservedQuantity,
        @Schema(description = "Part of the eligible on-hand held for QC on non-lot-tracked production "
                + "output (B2). Usually zero.")
        BigDecimal qualityHoldQuantity,
        @Schema(description = "max(0, max(safetyStock, reorderPoint) − availableQuantity): how much must "
                + "be replenished to clear the alert. Always 0 when status is OK.")
        BigDecimal shortageQuantity
) {}
