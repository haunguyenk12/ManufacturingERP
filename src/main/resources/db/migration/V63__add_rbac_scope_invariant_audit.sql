-- Security hardening for dynamic RBAC.
-- Existing malformed rows remain visible for controlled cleanup; this migration never widens
-- access and never guesses how legacy ownership should be repaired.

ALTER TABLE roles
    ADD CONSTRAINT chk_roles_reserved_admin_authoritative
    CHECK (
        UPPER(BTRIM(code)) NOT IN ('ADMIN', 'ROLE_ADMIN')
        OR (is_system = TRUE AND company_id IS NULL)
    ) NOT VALID;

CREATE OR REPLACE VIEW rbac_scope_invariant_violations AS
SELECT
    'RESERVED_CUSTOM_ROLE'::TEXT AS violation_type,
    r.role_id,
    NULL::UUID AS scope_id,
    NULL::UUID AS assignment_id,
    NULL::UUID AS scope_resource_id,
    'Reserved ADMIN code is not an authoritative global system role'::TEXT AS details
FROM roles r
WHERE UPPER(BTRIM(r.code)) IN ('ADMIN', 'ROLE_ADMIN')
  AND NOT (r.is_system = TRUE AND r.company_id IS NULL)

UNION ALL

SELECT
    'GLOBAL_SCOPE_HAS_RESOURCE',
    NULL::UUID,
    s.scope_id,
    NULL::UUID,
    sr.scope_resource_id,
    'GLOBAL scope contains an explicit resource'
FROM access_scopes s
JOIN access_scope_resources sr ON sr.scope_id = s.scope_id
WHERE s.scope_type = 'GLOBAL'

UNION ALL

SELECT
    'SCOPE_RESOURCE_TYPE_MISMATCH',
    NULL::UUID,
    s.scope_id,
    NULL::UUID,
    sr.scope_resource_id,
    'scope_type=' || s.scope_type || ', resource_type=' || sr.resource_type
FROM access_scopes s
JOIN access_scope_resources sr ON sr.scope_id = s.scope_id
WHERE (s.scope_type = 'COMPANY' AND sr.resource_type <> 'COMPANY')
   OR (s.scope_type = 'PLANT' AND sr.resource_type <> 'PLANT')
   OR (s.scope_type = 'WAREHOUSE_GROUP' AND sr.resource_type <> 'WAREHOUSE')
   OR s.scope_type = 'GLOBAL'

UNION ALL

SELECT
    'NON_GLOBAL_EMPTY_ACTIVE_ASSIGNMENT',
    a.role_id,
    s.scope_id,
    a.assignment_id,
    NULL::UUID,
    'Active assignment references a non-global scope without resources'
FROM user_role_assignments a
JOIN access_scopes s ON s.scope_id = a.scope_id
WHERE a.status = 'ACTIVE'
  AND s.scope_type <> 'GLOBAL'
  AND NOT EXISTS (
      SELECT 1 FROM access_scope_resources sr WHERE sr.scope_id = s.scope_id
  )

UNION ALL

SELECT
    'COMPANY_ROLE_ON_GLOBAL_SCOPE',
    r.role_id,
    s.scope_id,
    a.assignment_id,
    NULL::UUID,
    'Company-owned role is assigned to GLOBAL'
FROM user_role_assignments a
JOIN roles r ON r.role_id = a.role_id
JOIN access_scopes s ON s.scope_id = a.scope_id
WHERE a.status = 'ACTIVE'
  AND r.company_id IS NOT NULL
  AND s.scope_type = 'GLOBAL'

UNION ALL

SELECT
    'SYSTEM_ADMIN_ON_NON_GLOBAL_SCOPE',
    r.role_id,
    s.scope_id,
    a.assignment_id,
    NULL::UUID,
    'Authoritative system ADMIN is assigned outside GLOBAL'
FROM user_role_assignments a
JOIN roles r ON r.role_id = a.role_id
JOIN access_scopes s ON s.scope_id = a.scope_id
WHERE a.status = 'ACTIVE'
  AND r.is_system = TRUE
  AND r.company_id IS NULL
  AND UPPER(BTRIM(r.code)) = 'ADMIN'
  AND s.scope_type <> 'GLOBAL'

UNION ALL

SELECT
    'COMPANY_ROLE_RESOURCE_OWNERSHIP_MISMATCH',
    r.role_id,
    s.scope_id,
    a.assignment_id,
    sr.scope_resource_id,
    'Scope resource does not belong to role.company_id'
FROM user_role_assignments a
JOIN roles r ON r.role_id = a.role_id
JOIN access_scopes s ON s.scope_id = a.scope_id
JOIN access_scope_resources sr ON sr.scope_id = s.scope_id
LEFT JOIN plants p
       ON sr.resource_type = 'PLANT' AND p.plant_id = sr.resource_id
LEFT JOIN warehouses w
       ON sr.resource_type = 'WAREHOUSE' AND w.warehouse_id = sr.resource_id
LEFT JOIN plants wp ON wp.plant_id = w.plant_id
WHERE a.status = 'ACTIVE'
  AND r.company_id IS NOT NULL
  AND CASE sr.resource_type
          WHEN 'COMPANY' THEN sr.resource_id
          WHEN 'PLANT' THEN p.company_id
          WHEN 'WAREHOUSE' THEN wp.company_id
      END IS DISTINCT FROM r.company_id;

COMMENT ON VIEW rbac_scope_invariant_violations IS
    'Read-only security audit of malformed dynamic RBAC data. Empty is required before release.';
