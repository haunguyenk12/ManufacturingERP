-- V21__seed_purchasing_permissions.sql
-- Purchasing v1 permissions for dynamic RBAC.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_SUPPLIER_READ', 'SUPPLIER', 'READ', 'Read supplier and item-supplier master data'),
    ('PERM_SUPPLIER_MANAGE', 'SUPPLIER', 'MANAGE', 'Create, update, and deactivate suppliers and item suppliers'),
    ('PERM_PURCHASE_REQUISITION_READ', 'PURCHASE_REQUISITION', 'READ', 'Read purchase requisitions'),
    ('PERM_PURCHASE_REQUISITION_MANAGE', 'PURCHASE_REQUISITION', 'MANAGE', 'Create, approve, reject, cancel, and convert purchase requisitions'),
    ('PERM_PURCHASE_ORDER_READ', 'PURCHASE_ORDER', 'READ', 'Read purchase orders'),
    ('PERM_PURCHASE_ORDER_MANAGE', 'PURCHASE_ORDER', 'MANAGE', 'Create, send, and cancel purchase orders'),
    ('PERM_GOODS_RECEIPT_POST', 'GOODS_RECEIPT', 'POST', 'Post goods receipts into inventory')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_SUPPLIER_READ',
    'PERM_SUPPLIER_MANAGE',
    'PERM_PURCHASE_REQUISITION_READ',
    'PERM_PURCHASE_REQUISITION_MANAGE',
    'PERM_PURCHASE_ORDER_READ',
    'PERM_PURCHASE_ORDER_MANAGE',
    'PERM_GOODS_RECEIPT_POST'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_SUPPLIER_READ',
    'PERM_SUPPLIER_MANAGE',
    'PERM_PURCHASE_REQUISITION_READ',
    'PERM_PURCHASE_REQUISITION_MANAGE',
    'PERM_PURCHASE_ORDER_READ',
    'PERM_PURCHASE_ORDER_MANAGE',
    'PERM_GOODS_RECEIPT_POST'
)
WHERE r.code = 'MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_PURCHASE_REQUISITION_READ',
    'PERM_PURCHASE_ORDER_READ',
    'PERM_GOODS_RECEIPT_POST'
)
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
