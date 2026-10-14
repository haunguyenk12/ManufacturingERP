-- V70__seed_customer_permissions.sql
-- G2-1: permissions for the new Customer master data (NEXT_PHASE_PLAN.md §9.2 D7).
--
-- Customer is the sales-side mirror of Supplier, so it gets exactly the grant matrix PERM_SUPPLIER_*
-- has in this database at migration time — copied from role_permissions, not restated per role.
-- Restating ('ADMIN','MANAGER') would silently diverge from any custom role an operator configured
-- after V21; copying is the same technique V57 used for PERM_ITEM_*.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_CUSTOMER_READ', 'CUSTOMER', 'READ', 'Read customer master data'),
    ('PERM_CUSTOMER_MANAGE', 'CUSTOMER', 'MANAGE', 'Create, update, activate, and deactivate customers')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, target.permission_id
FROM role_permissions rp
JOIN permissions source ON source.permission_id = rp.permission_id
JOIN (VALUES
    ('PERM_SUPPLIER_READ', 'PERM_CUSTOMER_READ'),
    ('PERM_SUPPLIER_MANAGE', 'PERM_CUSTOMER_MANAGE')
) AS mapping(source_code, target_code) ON mapping.source_code = source.code
JOIN permissions target ON target.code = mapping.target_code
ON CONFLICT DO NOTHING;
