package com.erp.manufacturing.module.planning.domain;

import java.util.List;
import java.util.UUID;

/**
 * The validated request of a commercial run, stored on the run ({@code mrp_runs.input_snapshot}) so
 * a reloaded run shows what it was computed from.
 */
public record MrpRunInputSnapshot(
        List<UUID> salesOrderLineIds,
        List<ItemWarehouse> itemWarehouseMappings,
        boolean includeRop
) {
    public record ItemWarehouse(UUID itemId, UUID demandWarehouseId, UUID outputWarehouseId) {}
}
