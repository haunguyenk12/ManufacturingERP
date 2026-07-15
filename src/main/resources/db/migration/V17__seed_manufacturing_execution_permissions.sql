-- V17__seed_manufacturing_execution_permissions.sql
-- Manufacturing Execution v1 permissions for dynamic RBAC.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_MATERIAL_RESERVATION_MANAGE', 'MATERIAL_RESERVATION', 'MANAGE', 'Create, read, and release material reservations'),
    ('PERM_MATERIAL_ISSUE_MANAGE', 'MATERIAL_ISSUE', 'MANAGE', 'Post and read material issue documents'),
    ('PERM_PRODUCTION_RECEIPT_MANAGE', 'PRODUCTION_RECEIPT', 'MANAGE', 'Post and read production receipt documents'),
    ('PERM_WIP_MANAGE', 'WIP', 'MANAGE', 'Record and read WIP transactions'),
    ('PERM_WORK_ORDER_VARIANCE_READ', 'WORK_ORDER_VARIANCE', 'READ', 'Read work order variance reports')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_MATERIAL_RESERVATION_MANAGE',
    'PERM_MATERIAL_ISSUE_MANAGE',
    'PERM_PRODUCTION_RECEIPT_MANAGE',
    'PERM_WIP_MANAGE',
    'PERM_WORK_ORDER_VARIANCE_READ'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_MATERIAL_RESERVATION_MANAGE',
    'PERM_MATERIAL_ISSUE_MANAGE',
    'PERM_PRODUCTION_RECEIPT_MANAGE',
    'PERM_WIP_MANAGE',
    'PERM_WORK_ORDER_VARIANCE_READ'
)
WHERE r.code = 'MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_MATERIAL_ISSUE_MANAGE',
    'PERM_PRODUCTION_RECEIPT_MANAGE',
    'PERM_WIP_MANAGE'
)
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
