-- V6__create_organization_dynamic_rbac_audit_changes.sql
-- Organization foundation + dynamic RBAC + field-level audit detail.

-- Organization hierarchy
CREATE TABLE companies (
    company_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(100) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT       DEFAULT 0,
    CONSTRAINT uk_companies_code UNIQUE (code),
    CONSTRAINT chk_companies_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE plants (
    plant_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID         NOT NULL REFERENCES companies(company_id),
    code       VARCHAR(100) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    timezone   VARCHAR(100) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT       DEFAULT 0,
    CONSTRAINT uk_plants_company_code UNIQUE (company_id, code),
    CONSTRAINT chk_plants_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE warehouses (
    warehouse_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id     UUID         NOT NULL REFERENCES plants(plant_id),
    code         VARCHAR(100) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    type         VARCHAR(50)  NOT NULL DEFAULT 'GENERAL',
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT       DEFAULT 0,
    CONSTRAINT uk_warehouses_plant_code UNIQUE (plant_id, code),
    CONSTRAINT chk_warehouses_type CHECK (type IN (
        'RAW_MATERIAL', 'WIP', 'FINISHED_GOODS', 'QUALITY', 'SCRAP', 'GENERAL'
    )),
    CONSTRAINT chk_warehouses_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_plants_company_id ON plants(company_id);
CREATE INDEX idx_plants_status ON plants(status);
CREATE INDEX idx_warehouses_plant_id ON warehouses(plant_id);
CREATE INDEX idx_warehouses_status ON warehouses(status);
CREATE INDEX idx_warehouses_type ON warehouses(type);

-- Upgrade roles from fixed global RBAC to dynamic RBAC.
ALTER TABLE roles ADD COLUMN company_id UUID;
ALTER TABLE roles ADD COLUMN code VARCHAR(100);
ALTER TABLE roles ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE roles ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE roles ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE roles ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE roles ADD COLUMN created_by UUID;
ALTER TABLE roles ADD COLUMN updated_by UUID;
ALTER TABLE roles ADD COLUMN version BIGINT DEFAULT 0;

UPDATE roles
SET code = name,
    is_system = TRUE,
    status = 'ACTIVE'
WHERE code IS NULL;

ALTER TABLE roles ALTER COLUMN code SET NOT NULL;
ALTER TABLE roles ALTER COLUMN name TYPE VARCHAR(100);
ALTER TABLE roles ADD CONSTRAINT fk_roles_company FOREIGN KEY (company_id) REFERENCES companies(company_id);
ALTER TABLE roles ADD CONSTRAINT chk_roles_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_key;
CREATE UNIQUE INDEX uk_roles_system_code ON roles(code) WHERE company_id IS NULL;
CREATE UNIQUE INDEX uk_roles_company_code ON roles(company_id, code) WHERE company_id IS NOT NULL;
CREATE INDEX idx_roles_company_id ON roles(company_id);
CREATE INDEX idx_roles_status ON roles(status);

-- Dynamic permissions
CREATE TABLE permissions (
    permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(120) NOT NULL,
    resource      VARCHAR(80)  NOT NULL,
    action        VARCHAR(80)  NOT NULL,
    description   TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT       DEFAULT 0,
    CONSTRAINT uk_permissions_code UNIQUE (code),
    CONSTRAINT uk_permissions_resource_action UNIQUE (resource, action),
    CONSTRAINT chk_permissions_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(permission_id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- Flexible access scopes. Resource membership is validated by the service layer
-- because resource_id is polymorphic by resource_type.
CREATE TABLE access_scopes (
    scope_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(120) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    scope_type  VARCHAR(50)  NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       DEFAULT 0,
    CONSTRAINT uk_access_scopes_code UNIQUE (code),
    CONSTRAINT chk_access_scopes_type CHECK (scope_type IN ('GLOBAL', 'COMPANY', 'PLANT', 'WAREHOUSE_GROUP', 'CUSTOM')),
    CONSTRAINT chk_access_scopes_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE access_scope_resources (
    scope_resource_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_id          UUID        NOT NULL REFERENCES access_scopes(scope_id) ON DELETE CASCADE,
    resource_type     VARCHAR(50) NOT NULL,
    resource_id       UUID        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_scope_resource_type CHECK (resource_type IN ('COMPANY', 'PLANT', 'WAREHOUSE')),
    CONSTRAINT uk_scope_resource UNIQUE (scope_id, resource_type, resource_id)
);

CREATE INDEX idx_access_scope_resources_scope_id ON access_scope_resources(scope_id);
CREATE INDEX idx_access_scope_resources_resource ON access_scope_resources(resource_type, resource_id);

CREATE TABLE user_role_assignments (
    assignment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_id       UUID        NOT NULL REFERENCES roles(role_id),
    scope_id      UUID        NOT NULL REFERENCES access_scopes(scope_id),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT      DEFAULT 0,
    CONSTRAINT chk_user_role_assignments_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_user_role_assignment_active
    ON user_role_assignments(user_id, role_id, scope_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_user_role_assignments_user_id ON user_role_assignments(user_id);
CREATE INDEX idx_user_role_assignments_role_id ON user_role_assignments(role_id);
CREATE INDEX idx_user_role_assignments_scope_id ON user_role_assignments(scope_id);
CREATE INDEX idx_user_role_assignments_status ON user_role_assignments(status);

-- Field-level audit details
CREATE TABLE audit_log_changes (
    change_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id    UUID        NOT NULL REFERENCES audit_logs(audit_id) ON DELETE CASCADE,
    field_name  VARCHAR(150) NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    change_type VARCHAR(30) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_audit_log_changes_type CHECK (change_type IN ('CREATE', 'UPDATE', 'DELETE'))
);

CREATE INDEX idx_audit_log_changes_audit_id ON audit_log_changes(audit_id);
CREATE INDEX idx_audit_log_changes_field_name ON audit_log_changes(field_name);

