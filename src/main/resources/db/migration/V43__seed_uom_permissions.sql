-- V43__seed_uom_permissions.sql
-- C2-3: permissions for the new UOM master data.
--
-- OPERATOR gets READ (it needs to see the unit on-screen, same reasoning as PERM_BOM_READ granted in
-- V41) but not MANAGE — creating/editing/toggling the unit catalog is master-data configuration,
-- same tier as BOM/routing management, not shop-floor execution.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_UOM_READ', 'UOM', 'READ', 'Read the unit of measure catalog'),
    ('PERM_UOM_MANAGE', 'UOM', 'MANAGE', 'Create, update, activate, and deactivate units of measure')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_UOM_READ', 'PERM_UOM_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'PERM_UOM_READ'
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
