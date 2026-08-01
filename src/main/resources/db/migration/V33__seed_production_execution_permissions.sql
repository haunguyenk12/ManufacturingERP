-- V33__seed_production_execution_permissions.sql
-- Phase F5 permissions for Production Execution.
-- Reporting good/scrap/rework is the operator's core job, so OPERATOR gets MANAGE here — unlike
-- routing (V31) where the operator only reads.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_PRODUCTION_EXECUTION_READ', 'PRODUCTION_EXECUTION', 'READ', 'Read shop floor production execution reports'),
    ('PERM_PRODUCTION_EXECUTION_MANAGE', 'PRODUCTION_EXECUTION', 'MANAGE', 'Report good, scrap, and rework quantities from the shop floor')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_PRODUCTION_EXECUTION_READ', 'PERM_PRODUCTION_EXECUTION_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER', 'OPERATOR')
ON CONFLICT DO NOTHING;
