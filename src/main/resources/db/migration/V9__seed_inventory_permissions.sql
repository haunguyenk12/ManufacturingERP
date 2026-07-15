-- V9__seed_inventory_permissions.sql
-- Baseline inventory permissions for dynamic RBAC.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_INVENTORY_READ', 'INVENTORY', 'READ', 'Read item master, stock balances, and movements'),
    ('PERM_INVENTORY_MANAGE', 'INVENTORY', 'MANAGE', 'Create and update item master data'),
    ('PERM_INVENTORY_MOVE', 'INVENTORY', 'MOVE', 'Receive, issue, and adjust inventory stock')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_INVENTORY_READ',
    'PERM_INVENTORY_MANAGE',
    'PERM_INVENTORY_MOVE'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
