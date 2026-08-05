-- V47__seed_shift_and_work_calendar_permissions.sql
-- C2-7: permissions for Shift and Work Calendar master data.
--
-- Same tier as PERM_WORK_CENTER_READ/_MANAGE (V45): production master data, not company/plant/
-- warehouse organization structure. Two separate permission pairs (not one shared pair) because
-- gap doc §3.5 lists /shifts and /work-calendars as two distinct resource groups on the wire, even
-- though both entities live in the same module.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_SHIFT_READ', 'SHIFT', 'READ', 'Read shifts'),
    ('PERM_SHIFT_MANAGE', 'SHIFT', 'MANAGE', 'Create, update, activate, and deactivate shifts'),
    ('PERM_WORK_CALENDAR_READ', 'WORK_CALENDAR', 'READ', 'Read work calendars'),
    ('PERM_WORK_CALENDAR_MANAGE', 'WORK_CALENDAR', 'MANAGE', 'Create, update, activate, and deactivate work calendars')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN
    ('PERM_SHIFT_READ', 'PERM_SHIFT_MANAGE', 'PERM_WORK_CALENDAR_READ', 'PERM_WORK_CALENDAR_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_SHIFT_READ', 'PERM_WORK_CALENDAR_READ')
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
