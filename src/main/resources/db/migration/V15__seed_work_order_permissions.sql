-- V15__seed_work_order_permissions.sql
-- Baseline work order permissions for dynamic RBAC.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_WORK_ORDER_READ', 'WORK_ORDER', 'READ', 'Read work orders and component requirements'),
    ('PERM_WORK_ORDER_MANAGE', 'WORK_ORDER', 'MANAGE', 'Create, update, release, and cancel work orders'),
    ('PERM_WORK_ORDER_EXECUTE', 'WORK_ORDER', 'EXECUTE', 'Issue components and complete work order output')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_WORK_ORDER_READ',
    'PERM_WORK_ORDER_MANAGE',
    'PERM_WORK_ORDER_EXECUTE'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
