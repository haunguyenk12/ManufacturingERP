-- V51__seed_costing_permissions.sql
-- P3: permissions for the Costing Engine.
--
-- ADMIN + MANAGER only — NO grant to OPERATOR, unlike PERM_CAPACITY_READ (V49) or every other
-- master-data permission pair added since C2-5. Confirmed by reading
-- V17__seed_manufacturing_execution_permissions.sql: PERM_WORK_ORDER_VARIANCE_READ (the permission
-- this phase's cost figures piggyback onto, via GET /work-orders/{id}/variance) has always been
-- ADMIN+MANAGER only. Cost data is treated as sensitive, unlike Work Center/Shift where OPERATOR
-- needs read access for daily execution.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_COSTING_READ', 'COSTING', 'READ', 'Read item standard costs and work order cost variance'),
    ('PERM_COSTING_MANAGE', 'COSTING', 'MANAGE', 'Create or update item standard costs')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_COSTING_READ', 'PERM_COSTING_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
