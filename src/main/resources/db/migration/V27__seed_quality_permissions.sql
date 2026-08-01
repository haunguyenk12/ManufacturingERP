-- V27__seed_quality_permissions.sql
-- Phase F2 permission for QC disposition.
-- Separation of duties, same reasoning as V24: whoever produced the goods must not be the one
-- clearing them through quality. Granted to ADMIN and MANAGER only, not OPERATOR.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_QUALITY_DISPOSITION', 'QUALITY', 'DISPOSITION', 'Release approved production output from HOLD to AVAILABLE or REJECTED')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'PERM_QUALITY_DISPOSITION'
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
