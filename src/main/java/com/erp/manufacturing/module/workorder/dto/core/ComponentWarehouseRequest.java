package com.erp.manufacturing.module.workorder.dto.core;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * G8a (D2): which warehouse a component item is reserved from. Keyed by the component <em>item</em>,
 * not the line, so the same shape works on create (no line ids yet) and later; it applies to every
 * line of that item — the BOM can list one item on several lines and the work order keeps one line per
 * BOM line (FE Q6).
 */
public record ComponentWarehouseRequest(
        @NotNull UUID itemId,
        @NotNull UUID warehouseId
) {}
