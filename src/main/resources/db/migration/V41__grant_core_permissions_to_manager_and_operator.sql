-- V41__grant_core_permissions_to_manager_and_operator.sql
-- C2-5: reconcile the RBAC seed with docs/roles-and-permissions.md.
--
-- The defect this closes: the early seeds (V7..V15) granted their permissions to ADMIN only, while
-- every later module seed (V17..V33) granted ADMIN + MANAGER + OPERATOR. The result was that 12 core
-- permissions — including PERM_WORK_ORDER_READ — belonged to ADMIN alone, so a MANAGER account got
-- 403 PERMISSION_DENIED on the work order list of its own plant. Measured, not inferred: logging in
-- as a plant-scoped MANAGER and calling GET /plants/{id}/work-orders returned
-- {"code":"PERMISSION_DENIED"} before this migration.
--
-- docs/roles-and-permissions.md is the authority here (user decision, 2026-08-04): its MANAGER
-- section and RACI matrix make MANAGER accountable for creating/releasing work orders, managing BOMs
-- and inventory movements. The code had drifted from it, not the other way round.
--
-- Deliberately NOT granted (the two "configure the system" permissions the doc reserves for ADMIN):
--   PERM_ORG_MANAGE    — create/deactivate company, plant, warehouse
--   PERM_ACCESS_MANAGE — roles, permissions, scopes, assignments
-- These two are the entire admin-only set after this migration, and
-- FlywayMigrationIT.migrate_v41_* pins that list so the next new permission cannot quietly become
-- admin-only again.
--
-- Separation of duties is preserved (docs "Separation of duties (P1 + F2)"): OPERATOR still does NOT
-- get PERM_MATERIAL_ISSUE_OVERRIDE, PERM_PRODUCTION_RECEIPT_APPROVE or PERM_QUALITY_DISPOSITION, and
-- does not get PERM_WORK_ORDER_MANAGE — creating and releasing a work order stays with MANAGER.

-- ── MANAGER ───────────────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_WORK_ORDER_READ',      -- list/get work orders
    'PERM_WORK_ORDER_MANAGE',    -- create, update, plan, release, cancel
    'PERM_WORK_ORDER_EXECUTE',   -- issue components, complete output
    'PERM_BOM_READ',
    'PERM_BOM_MANAGE',           -- RACI: MANAGER is accountable for BOM create/activate
    'PERM_INVENTORY_READ',
    'PERM_INVENTORY_MOVE',       -- receive / issue / adjust movements
    'PERM_INVENTORY_MANAGE',     -- item master + item warehouse settings
    'PERM_ORG_READ',
    'PERM_PLANNING_READ'
)
WHERE r.code = 'MANAGER'
ON CONFLICT DO NOTHING;

-- ── OPERATOR ──────────────────────────────────────────────────────────────────
-- Reads it needs to do its job, plus the stock movements its own doc section lists
-- (STOCK_RECEIVE / STOCK_ISSUE / STOCK_ADJUST → PERM_INVENTORY_MOVE) and the legacy single-line
-- execution endpoints (PERM_WORK_ORDER_EXECUTE guards issueComponent + completeOutput).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_WORK_ORDER_READ',      -- cannot issue material to a work order it cannot open
    'PERM_WORK_ORDER_EXECUTE',
    'PERM_BOM_READ',             -- component requirements come from the BOM snapshot
    'PERM_INVENTORY_READ',
    'PERM_INVENTORY_MOVE',
    'PERM_ORG_READ'              -- needs to see the plants/warehouses it works in
)
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
