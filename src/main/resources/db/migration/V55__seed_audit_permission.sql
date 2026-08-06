-- V55__seed_audit_permission.sql
-- C2-1: permission for the Audit Logs read API.
--
-- ADMIN only (confirmed with user) — audit trail exposes every user's IP/user-agent/action history,
-- the same sensitivity class as PERM_ORG_MANAGE/PERM_ACCESS_MANAGE (the only two other ADMIN-only
-- permissions in the system, see migrate_v41_leavesNoCorePermissionGrantedToAdminAlone), not the
-- PERM_COSTING_READ class (ADMIN+MANAGER).

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_AUDIT_READ', 'AUDIT', 'READ', 'Read audit log entries')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'PERM_AUDIT_READ'
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
