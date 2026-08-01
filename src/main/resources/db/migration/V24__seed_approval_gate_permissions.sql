-- V24__seed_approval_gate_permissions.sql
-- Phase P1 permissions for the approval gates.
-- Separation of duties: OPERATOR posts documents but must NOT approve its own
-- over-issue or production receipt. Only ADMIN and MANAGER receive these.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_MATERIAL_ISSUE_OVERRIDE', 'MATERIAL_ISSUE', 'OVERRIDE', 'Issue material beyond BOM requirement with justification'),
    ('PERM_PRODUCTION_RECEIPT_APPROVE', 'PRODUCTION_RECEIPT', 'APPROVE', 'Approve or reject pending production receipts')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_MATERIAL_ISSUE_OVERRIDE',
    'PERM_PRODUCTION_RECEIPT_APPROVE'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_MATERIAL_ISSUE_OVERRIDE',
    'PERM_PRODUCTION_RECEIPT_APPROVE'
)
WHERE r.code = 'MANAGER'
ON CONFLICT DO NOTHING;
