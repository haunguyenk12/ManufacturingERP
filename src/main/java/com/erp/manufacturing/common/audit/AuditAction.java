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
    /** D8c: admin manually cleared a fail-count lockout and reactivated the account. */
    ACCOUNT_UNLOCKED,
    /** RTR (D8a): a refresh token that was already rotated away came back — all sessions revoked. */
    SUSPICIOUS_TOKEN_REUSE,
    /** B81 (D8b): a session outlived the absolute timeout and was retired even though it was in use. */
    SESSION_ABSOLUTE_TIMEOUT,
    /** D8c: password changed via the forgot-password / reset-token flow. */
    PASSWORD_RESET,

    // ── User Management ────────────────────────────────────────────────────
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    PASSWORD_CHANGED,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    ROLE_CREATED,
    /** C2-4: role/scope lifecycle (BACKEND_CAPSTONE2_API_GAPS.md §4.4/§4.5). */
    ROLE_UPDATED,
    ROLE_ACTIVATED,
    ROLE_DEACTIVATED,
    PERMISSION_CREATED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    ACCESS_SCOPE_CREATED,
    ACCESS_SCOPE_RESOURCE_ADDED,
    SCOPE_UPDATED,
    SCOPE_ACTIVATED,
    SCOPE_DEACTIVATED,
    USER_ROLE_SCOPE_ASSIGNED,
    USER_ROLE_SCOPE_REVOKED,
    COMPANY_CREATED,
    COMPANY_UPDATED,
    COMPANY_DEACTIVATED,
    PLANT_CREATED,
    PLANT_UPDATED,
    PLANT_DEACTIVATED,
    WAREHOUSE_CREATED,
    WAREHOUSE_UPDATED,
    WAREHOUSE_DEACTIVATED,

    // ── Manufacturing ──────────────────────────────────────────────────────
    BOM_CREATED,
    BOM_UPDATED,
    BOM_DELETED,
    BOM_ACTIVATED,
    ROUTING_CREATED,
    ROUTING_ACTIVATED,
    ROUTING_DEACTIVATED,
    WORK_ORDER_CREATED,
    WORK_ORDER_UPDATED,
    WORK_ORDER_RELEASED,
    WORK_ORDER_COMPONENT_ISSUED,
    WORK_ORDER_COMPLETED,
    WORK_ORDER_CANCELLED,
    WORK_ORDER_CLOSED,
    WORK_ORDER_OPERATION_SCHEDULE_ADJUSTED,
    MATERIAL_RESERVED,
    MATERIAL_RESERVATION_RELEASED,
    MATERIAL_ISSUE_POSTED,
    PRODUCTION_RECEIPT_CREATED,
    PRODUCTION_RECEIPT_SUBMITTED,
    /** Kept under the original name so historical audit_logs rows stay searchable after the
     *  ProductionReceiptStatus POSTED → APPROVED rename (F2). */
    PRODUCTION_RECEIPT_POSTED,
    PRODUCTION_RECEIPT_REJECTED,
    QC_DISPOSITION_RECORDED,
    /** Shop-floor good/scrap/rework report (F5) — the event that now advances a work order. */
    PRODUCTION_EXECUTION_REPORTED,
    WIP_TRANSACTION_RECORDED,
    SCRAP_REPORTED,
    REWORK_REPORTED,
    SALES_ORDER_CREATED,
    SALES_ORDER_UPDATED,
    SALES_ORDER_CONFIRMED,
    SALES_ORDER_CANCELLED,
    MRP_RUN,
    PLANNING_DEMAND_CREATED,
    PLANNING_DEMAND_CANCELLED,
    MRP_RUN_CREATED,
    MRP_RUN_COMPLETED,
    MRP_RUN_FAILED,
    SUPPLY_SUGGESTION_APPROVED,
    SUPPLY_SUGGESTION_REJECTED,
    SUPPLY_SUGGESTION_CONVERTED,
    SUPPLIER_CREATED,
    SUPPLIER_UPDATED,
    SUPPLIER_DEACTIVATED,
    ITEM_SUPPLIER_CREATED,
    ITEM_SUPPLIER_UPDATED,
    /** C2-3: unit of measure master data (BACKEND_CAPSTONE2_API_GAPS.md §3.1). */
    UOM_CREATED,
    UOM_UPDATED,
    UOM_ACTIVATED,
    UOM_DEACTIVATED,
    /** C2-6: work center master data (BACKEND_CAPSTONE2_API_GAPS.md §3.4). */
    WORK_CENTER_CREATED,
    WORK_CENTER_UPDATED,
    WORK_CENTER_ACTIVATED,
    WORK_CENTER_DEACTIVATED,
    /** C2-7: shift + work calendar master data (BACKEND_CAPSTONE2_API_GAPS.md §3.5). */
    SHIFT_CREATED,
    SHIFT_UPDATED,
    SHIFT_ACTIVATED,
    SHIFT_DEACTIVATED,
    WORK_CALENDAR_CREATED,
    WORK_CALENDAR_UPDATED,
    WORK_CALENDAR_ACTIVATED,
    WORK_CALENDAR_DEACTIVATED,
    PURCHASE_REQUISITION_CREATED,
    PURCHASE_REQUISITION_APPROVED,
    PURCHASE_REQUISITION_REJECTED,
    PURCHASE_REQUISITION_CANCELLED,
    PURCHASE_REQUISITION_CONVERTED,
    PURCHASE_ORDER_CREATED,
    PURCHASE_ORDER_SENT,
    PURCHASE_ORDER_CANCELLED,
    GOODS_RECEIPT_POSTED,
    GOODS_RECEIPT_CANCELLED,
    ITEM_CREATED,
    ITEM_UPDATED,
    ITEM_DEACTIVATED,
    INVENTORY_RECEIVED,
    INVENTORY_ISSUED,
    INVENTORY_ADJUSTED,
    /**
     * P3: item standard cost master data (NEXT_PHASE_PLAN.md "P3 — Costing Engine"). One action,
     * not CREATED/UPDATED — the write path is a single upsert (PUT, matches
     * ItemWarehouseSettingController.upsert) and create-vs-update is not distinguishable through
     * {@code @Auditable} without either a self-invocation AOP hazard or a manual
     * RequestContextHolder call that would break plain-mock unit tests of the service.
     */
    ITEM_STANDARD_COST_UPSERTED,

    // ── System / Admin ─────────────────────────────────────────────────────
    RATE_LIMIT_EXCEEDED,
    CONFIG_CHANGED
}
