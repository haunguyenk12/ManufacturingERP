-- V45__seed_work_center_permissions.sql
-- C2-6: permissions for the new Work Center master data.
--
-- Same tier as PERM_ROUTING_READ/_MANAGE (V31), not PERM_ORG_*: Work Center is production master
-- data that constrains routing/capacity, not company/plant/warehouse organization structure.
-- OPERATOR gets READ (needs to see the work center on the operation being run) but not MANAGE.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_WORK_CENTER_READ', 'WORK_CENTER', 'READ', 'Read work centers'),
    ('PERM_WORK_CENTER_MANAGE', 'WORK_CENTER', 'MANAGE', 'Create, update, activate, and deactivate work centers')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_WORK_CENTER_READ', 'PERM_WORK_CENTER_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'PERM_WORK_CENTER_READ'
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
