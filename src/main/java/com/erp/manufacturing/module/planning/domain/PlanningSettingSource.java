package com.erp.manufacturing.module.planning.domain;

/**
 * Where the safety stock / reorder point / lead time used to net a requirement line came from
 * (spec §2.4 {@code settingSource}). {@link #SYSTEM_DEFAULT} means no ACTIVE item-warehouse setting
 * existed and the numbers fell back to zero — the planner sees {@code SYSTEM_FALLBACK_USED} on the
 * resulting proposal.
 */
public enum PlanningSettingSource {
    ITEM_WAREHOUSE,
    SYSTEM_DEFAULT
}
