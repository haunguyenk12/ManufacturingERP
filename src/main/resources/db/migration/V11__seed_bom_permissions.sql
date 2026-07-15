-- V11__seed_bom_permissions.sql
-- Baseline BOM permissions for dynamic RBAC.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_BOM_READ', 'BOM', 'READ', 'Read BOM headers, lines, and BOM trees'),
    ('PERM_BOM_MANAGE', 'BOM', 'MANAGE', 'Create, update, deactivate, and activate BOMs')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_BOM_READ',
    'PERM_BOM_MANAGE'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
