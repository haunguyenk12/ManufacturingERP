package com.erp.manufacturing.common.audit;

/**
 * All auditable actions across the system.
 * Add new actions here when implementing new modules.
 */
public enum AuditAction {

    // ── Auth ───────────────────────────────────────────────────────────────
    LOGIN,
    LOGOUT,
    LOGOUT_ALL,
    TOKEN_REFRESHED,
    SESSION_KICKED,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,

    // ── User Management ────────────────────────────────────────────────────
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    PASSWORD_CHANGED,
    ROLE_ASSIGNED,
    ROLE_REVOKED,

    // ── Manufacturing ──────────────────────────────────────────────────────
    BOM_CREATED,
    BOM_UPDATED,
    BOM_DELETED,
    WORK_ORDER_CREATED,
    WORK_ORDER_RELEASED,
    WORK_ORDER_COMPLETED,
    WORK_ORDER_CANCELLED,
    MRP_RUN,
    INVENTORY_ADJUSTED,

    // ── System / Admin ─────────────────────────────────────────────────────
    RATE_LIMIT_EXCEEDED,
    CONFIG_CHANGED
}
