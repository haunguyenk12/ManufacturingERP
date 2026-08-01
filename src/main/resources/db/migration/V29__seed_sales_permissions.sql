-- V29__seed_sales_permissions.sql
-- Phase F3 permissions for Sales Order.
-- Confirming/cancelling a sales order commits the plant to production, so MANAGE goes to
-- ADMIN and MANAGER only; OPERATOR gets READ so shop floor can trace which order a
-- work order originates from.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_SALES_ORDER_READ', 'SALES_ORDER', 'READ', 'Read sales orders and their lines'),
    ('PERM_SALES_ORDER_MANAGE', 'SALES_ORDER', 'MANAGE', 'Create, confirm, and cancel sales orders')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_SALES_ORDER_READ', 'PERM_SALES_ORDER_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'PERM_SALES_ORDER_READ'
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
