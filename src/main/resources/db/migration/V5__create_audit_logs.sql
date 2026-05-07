-- V5__create_audit_logs.sql
-- Immutable audit trail – append-only, never modified or soft-deleted

CREATE TABLE audit_logs (
    audit_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,                           -- NULL for unauthenticated events
    username    VARCHAR(100),                   -- snapshot at time of action
    action      VARCHAR(100) NOT NULL,          -- AuditAction enum value
    entity_type VARCHAR(100),                   -- Java class name, NULL for auth events
    entity_id   VARCHAR(255),                   -- PK of affected entity
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',  -- SUCCESS | FAILURE
    client_ip   VARCHAR(45),
    user_agent  VARCHAR(512),
    trace_id    VARCHAR(32),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
    -- NO updated_at, NO version – immutable by design
);

COMMENT ON TABLE  audit_logs             IS 'Immutable audit trail – append only';
COMMENT ON COLUMN audit_logs.action      IS 'AuditAction enum name';
COMMENT ON COLUMN audit_logs.entity_type IS 'Java entity class name (WorkOrder, BomHeader, etc.)';
COMMENT ON COLUMN audit_logs.status      IS 'SUCCESS or FAILURE';
COMMENT ON COLUMN audit_logs.user_id     IS 'NULL for unauthenticated events (failed logins)';

-- Indexes aligned with common query patterns
CREATE INDEX idx_audit_user_id    ON audit_logs(user_id);
CREATE INDEX idx_audit_action     ON audit_logs(action);
CREATE INDEX idx_audit_entity     ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_trace_id   ON audit_logs(trace_id);
-- Composite: "show all actions by this user in time range"
CREATE INDEX idx_audit_user_time  ON audit_logs(user_id, created_at DESC);
