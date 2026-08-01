-- V31__seed_routing_permissions.sql
-- Phase F4 permissions for Routing.
-- Routing is master data that constrains what the shop floor may produce, so MANAGE goes to
-- ADMIN and MANAGER only; OPERATOR gets READ to see the operations of the work order being run.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_ROUTING_READ', 'ROUTING', 'READ', 'Read routings and their operations'),
    ('PERM_ROUTING_MANAGE', 'ROUTING', 'MANAGE', 'Create, activate, and deactivate routings')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_ROUTING_READ', 'PERM_ROUTING_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'PERM_ROUTING_READ'
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
