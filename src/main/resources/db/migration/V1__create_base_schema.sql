-- V1__create_base_schema.sql
-- Manufacturing ERP – Base schema

-- ── User & Auth tables ──────────────────────────────────────────────────────

CREATE TABLE users (
    user_id     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(100) UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       DEFAULT 0
);

COMMENT ON TABLE  users            IS 'System users (all roles)';
COMMENT ON COLUMN users.status     IS 'ACTIVE | INACTIVE | LOCKED';
COMMENT ON COLUMN users.password   IS 'BCrypt encoded';
COMMENT ON COLUMN users.created_by IS 'FK -> users.user_id (who created this user)';
COMMENT ON COLUMN users.updated_by IS 'FK -> users.user_id (who last modified)';

CREATE TABLE roles (
    role_id     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50) UNIQUE NOT NULL,
    description TEXT
);

COMMENT ON TABLE roles      IS 'RBAC roles: ADMIN, MANAGER, OPERATOR';
COMMENT ON COLUMN roles.name IS 'Plain name without ROLE_ prefix';

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ── Indexes ─────────────────────────────────────────────────────────────────

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email    ON users(email);
CREATE INDEX idx_users_status   ON users(status);

-- ── Seed default roles ───────────────────────────────────────────────────────

INSERT INTO roles (name, description) VALUES
    ('ADMIN',    'System administrator with full access'),
    ('MANAGER',  'Production/inventory manager'),
    ('OPERATOR', 'Shop floor operator with limited access');
