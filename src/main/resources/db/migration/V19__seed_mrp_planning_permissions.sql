-- V19__seed_mrp_planning_permissions.sql
-- MRP & Procurement Planning v1 permissions for dynamic RBAC.

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_PLANNING_DEMAND_READ', 'PLANNING_DEMAND', 'READ', 'Read planning demands'),
    ('PERM_PLANNING_DEMAND_MANAGE', 'PLANNING_DEMAND', 'MANAGE', 'Create and cancel planning demands'),
    ('PERM_MRP_RUN', 'MRP', 'RUN', 'Run MRP planning calculations'),
    ('PERM_MRP_READ', 'MRP', 'READ', 'Read MRP runs, requirements, and suggestions'),
    ('PERM_SUPPLY_SUGGESTION_MANAGE', 'SUPPLY_SUGGESTION', 'MANAGE', 'Approve, reject, and convert MRP supply suggestions')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_PLANNING_DEMAND_READ',
    'PERM_PLANNING_DEMAND_MANAGE',
    'PERM_MRP_RUN',
    'PERM_MRP_READ',
    'PERM_SUPPLY_SUGGESTION_MANAGE'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_PLANNING_DEMAND_READ',
    'PERM_PLANNING_DEMAND_MANAGE',
    'PERM_MRP_RUN',
    'PERM_MRP_READ',
    'PERM_SUPPLY_SUGGESTION_MANAGE'
)
WHERE r.code = 'MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_PLANNING_DEMAND_READ',
    'PERM_MRP_READ'
)
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
