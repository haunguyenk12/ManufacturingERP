-- V49__seed_capacity_permissions.sql
-- C2-8: permissions for the Capacity Board + schedule adjustments.
--
-- Same tier as PERM_WORK_CENTER_READ/_MANAGE (V45) and PERM_SHIFT_READ/_MANAGE (V47): production
-- master-data/capacity, not company/plant/warehouse organization structure. OPERATOR gets read-only
-- — gap doc §3.6 says "Manager điều chỉnh một operation", so schedule-adjustments is ADMIN+MANAGER
-- only.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_CAPACITY_READ', 'CAPACITY', 'READ', 'Read the capacity board'),
    ('PERM_CAPACITY_MANAGE', 'CAPACITY', 'MANAGE', 'Adjust a work order operation''s schedule')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_CAPACITY_READ', 'PERM_CAPACITY_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'PERM_CAPACITY_READ'
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
