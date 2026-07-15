-- V7__seed_dynamic_rbac_baseline.sql
-- Baseline dynamic RBAC data while preserving legacy user_roles compatibility.

INSERT INTO access_scopes (code, name, scope_type, description, status)
VALUES ('GLOBAL_ALL', 'Global access', 'GLOBAL', 'System-wide access scope for platform administrators', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, resource, action, description, status)
VALUES
    ('PERM_ORG_READ',      'ORGANIZATION',   'READ',   'Read company, plant and warehouse master data', 'ACTIVE'),
    ('PERM_ORG_MANAGE',    'ORGANIZATION',   'MANAGE', 'Create and update company, plant and warehouse master data', 'ACTIVE'),
    ('PERM_ACCESS_MANAGE', 'ACCESS_CONTROL', 'MANAGE', 'Manage roles, permissions, access scopes and role assignments', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_ORG_READ', 'PERM_ORG_MANAGE', 'PERM_ACCESS_MANAGE')
WHERE r.company_id IS NULL
  AND r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_role_assignments (user_id, role_id, scope_id, status)
SELECT ur.user_id, ur.role_id, s.scope_id, 'ACTIVE'
FROM user_roles ur
JOIN access_scopes s ON s.code = 'GLOBAL_ALL'
WHERE NOT EXISTS (
    SELECT 1
    FROM user_role_assignments existing
    WHERE existing.user_id = ur.user_id
      AND existing.role_id = ur.role_id
      AND existing.scope_id = s.scope_id
      AND existing.status = 'ACTIVE'
);
