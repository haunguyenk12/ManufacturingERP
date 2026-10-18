package com.erp.manufacturing.module.planning.domain;

/** Frozen explanation of how MRP selected the warehouse for one requirement. */
public enum WarehouseResolutionSource {
    DEMAND_WAREHOUSE,
    RUN_DEMAND_WAREHOUSE,
    /** Warehouse the planner picked for this item on a commercial run (G6b); master data unchanged. */
    RUN_ITEM_MAPPING,
    SINGLE_ACTIVE_WAREHOUSE,
    ITEM_WAREHOUSE_DEFAULT,
    ITEM_WAREHOUSE_ONLY,
    WAREHOUSE_TYPE_FALLBACK,
    UNRESOLVED
}
