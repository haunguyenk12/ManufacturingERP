-- dev-seed.sql — demo organisation + role-scoped accounts for local development and FE testing.
--
-- Added in C2-5 to close the FE request "at least two Plants and an account per role to test
-- isolation" (BACKEND_CAPSTONE2_API_GAPS.md §5).
--
-- 🔴 RUN THIS BY HAND. It is deliberately NOT a Flyway migration and NOT wired into
--    spring.flyway.locations, for two reasons:
--      1. Demo accounts with a published password must never reach a real environment. Every
--         versioned migration under db/migration runs in EVERY environment.
--      2. `spring.profiles.active` defaults to `dev` (application.yml), and that default also
--         applies while tests run. Wiring this into application-dev.yml would inject demo rows into
--         the Testcontainers database of all 11 *IT classes and quietly change what they assert.
--
--    Usage (dev stack from docker-compose):
--      docker exec -i erp-postgres psql -U postgres -d manufacturing_erp < src/main/resources/db/dev-seed.sql
--
-- Idempotent: safe to run repeatedly. Every statement either ON CONFLICT DO NOTHING or guards with
-- NOT EXISTS (user_role_assignments has no unique constraint, so it needs the explicit guard).
--
-- Accounts created — all share the password `Admin@123` (same BCrypt hash as V2's admin):
--   manager.a   MANAGER  scoped to PLANT-A only
--   operator.a  OPERATOR scoped to PLANT-A only
--   manager.b   MANAGER  scoped to PLANT-B only   ← use this pair to prove plant isolation
-- The pre-existing `admin` account keeps its GLOBAL_ALL scope from V7.
--
-- Isolation check this data is meant to support: manager.a calling a PLANT-B endpoint must get
-- 403 PERMISSION_DENIED, not an empty list. An empty list would mean the scope filter silently
-- widened, which is the failure mode a single-plant fixture can never detect.

BEGIN;

-- ── Organisation ──────────────────────────────────────────────────────────────

INSERT INTO companies (code, name, status)
VALUES ('DEMO-CO', 'Demo Manufacturing Co.', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO plants (company_id, code, name, status)
SELECT c.company_id, v.code, v.name, 'ACTIVE'
FROM companies c
CROSS JOIN (VALUES
    ('PLANT-A', 'Demo Plant A'),
    ('PLANT-B', 'Demo Plant B')
) AS v(code, name)
WHERE c.code = 'DEMO-CO'
ON CONFLICT (company_id, code) DO NOTHING;

INSERT INTO warehouses (plant_id, code, name, type, status)
SELECT p.plant_id, v.code, v.name, v.type, 'ACTIVE'
FROM plants p
JOIN companies c ON c.company_id = p.company_id AND c.code = 'DEMO-CO'
CROSS JOIN (VALUES
    ('WH-RM',  'Raw material store', 'RAW_MATERIAL'),
    ('WH-WIP', 'Work in progress',   'WIP'),
    ('WH-FG',  'Finished goods',     'FINISHED_GOODS')
) AS v(code, name, type)
WHERE p.code IN ('PLANT-A', 'PLANT-B')
ON CONFLICT (plant_id, code) DO NOTHING;

-- ── Access scopes ─────────────────────────────────────────────────────────────
-- One PLANT scope per plant. These are what make the isolation test meaningful: the assignment
-- carries the scope, and the permission guard resolves a request's plant against its resources.

INSERT INTO access_scopes (code, name, scope_type, description, status)
VALUES
    ('DEMO_PLANT_A', 'Demo Plant A only', 'PLANT', 'Dev seed: access limited to PLANT-A', 'ACTIVE'),
    ('DEMO_PLANT_B', 'Demo Plant B only', 'PLANT', 'Dev seed: access limited to PLANT-B', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO access_scope_resources (scope_id, resource_type, resource_id)
SELECT s.scope_id, 'PLANT', p.plant_id
FROM access_scopes s
JOIN plants p ON p.code = 'PLANT-' || right(s.code, 1)
JOIN companies c ON c.company_id = p.company_id AND c.code = 'DEMO-CO'
WHERE s.code IN ('DEMO_PLANT_A', 'DEMO_PLANT_B')
ON CONFLICT (scope_id, resource_type, resource_id) DO NOTHING;

-- The warehouses of each plant are added to the same scope, so warehouse-scoped guards
-- (inventory receive/issue) resolve for these users too.
INSERT INTO access_scope_resources (scope_id, resource_type, resource_id)
SELECT s.scope_id, 'WAREHOUSE', w.warehouse_id
FROM access_scopes s
JOIN plants p ON p.code = 'PLANT-' || right(s.code, 1)
JOIN companies c ON c.company_id = p.company_id AND c.code = 'DEMO-CO'
JOIN warehouses w ON w.plant_id = p.plant_id
WHERE s.code IN ('DEMO_PLANT_A', 'DEMO_PLANT_B')
ON CONFLICT (scope_id, resource_type, resource_id) DO NOTHING;

-- ── Accounts ──────────────────────────────────────────────────────────────────
-- BCrypt(cost 12) hash of 'Admin@123', reused from V2__seed_admin_user.sql on purpose: one password
-- for every dev account is one less thing to look up, and this file never leaves dev.

INSERT INTO users (username, email, password, status)
VALUES
    ('manager.a',  'manager.a@erp.local',  '$2a$12$zUhsQTqnZs0KqpFHioEgpOXyJtVwLyWdwZsgaAl49LA5ocVTYwgqe', 'ACTIVE'),
    ('operator.a', 'operator.a@erp.local', '$2a$12$zUhsQTqnZs0KqpFHioEgpOXyJtVwLyWdwZsgaAl49LA5ocVTYwgqe', 'ACTIVE'),
    ('manager.b',  'manager.b@erp.local',  '$2a$12$zUhsQTqnZs0KqpFHioEgpOXyJtVwLyWdwZsgaAl49LA5ocVTYwgqe', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

-- ── Role assignments (dynamic RBAC) ───────────────────────────────────────────
-- Authorities come from user_role_assignments, not from the legacy user_roles table
-- (UserDetailsServiceImpl reads assignmentRepository), so this is the table that matters.

INSERT INTO user_role_assignments (user_id, role_id, scope_id, status)
SELECT u.user_id, r.role_id, s.scope_id, 'ACTIVE'
FROM (VALUES
    ('manager.a',  'MANAGER',  'DEMO_PLANT_A'),
    ('operator.a', 'OPERATOR', 'DEMO_PLANT_A'),
    ('manager.b',  'MANAGER',  'DEMO_PLANT_B')
) AS v(username, role_code, scope_code)
JOIN users u         ON u.username = v.username
JOIN roles r         ON r.code     = v.role_code
JOIN access_scopes s ON s.code     = v.scope_code
WHERE NOT EXISTS (
    SELECT 1 FROM user_role_assignments a
    WHERE a.user_id = u.user_id AND a.role_id = r.role_id AND a.scope_id = s.scope_id
);

COMMIT;

-- Verification (expects 3 rows: manager.a/MANAGER/DEMO_PLANT_A, operator.a/OPERATOR/DEMO_PLANT_A,
-- manager.b/MANAGER/DEMO_PLANT_B):
--   SELECT u.username, r.code AS role, s.code AS scope
--   FROM user_role_assignments a
--   JOIN users u ON u.user_id = a.user_id
--   JOIN roles r ON r.role_id = a.role_id
--   JOIN access_scopes s ON s.scope_id = a.scope_id
--   WHERE u.username IN ('manager.a', 'operator.a', 'manager.b');
