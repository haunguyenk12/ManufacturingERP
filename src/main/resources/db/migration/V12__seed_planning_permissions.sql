-- V12__seed_planning_permissions.sql
-- Baseline planning permissions for production estimation and shortage reports.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_PLANNING_READ', 'PLANNING', 'READ', 'Run production estimation and read shortage reports')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'PERM_PLANNING_READ'
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
