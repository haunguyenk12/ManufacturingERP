-- V67 — Audit refactor AR-2/AR-3/AR-6: transactional outbox, richer event model, multi-entity targets.
--
-- Fully additive. No existing column is dropped or renamed and no historical row is rewritten:
-- every new column is nullable and stays NULL for rows written before this migration, which is the
-- honest answer for a snapshot nobody captured at the time. Backfilling any of them from today's
-- data would falsify history (AuditRefactorPlan §2, §5.2).

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Transactional outbox
--
-- The reason this table exists: an audit event used to be published as a Spring event and persisted
-- by an AFTER_COMMIT listener on a separate thread and a separate transaction. Between the business
-- COMMIT and that INSERT there is a window in which the process can die, the thread pool can reject,
-- or the INSERT itself can fail — and the business change is already durable. The outbox row is
-- written inside the SAME transaction as the business change, so the two commit or roll back
-- together, and a dispatcher moves it to the audit tables afterwards, retrying until it lands.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE audit_outbox (
    outbox_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Producer-assigned identity. Carried onto audit_logs.event_id, where a unique index turns
    -- "retry after a crash" into "no duplicate".
    event_id        UUID         NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count   INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Error CATEGORY only. A raw driver message can quote the row that failed, which is how a
    -- secret ends up in a table that is meant to be safe to read.
    last_error_code VARCHAR(100),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    -- Lease: a row claimed by a worker that then crashed is reclaimed once locked_at ages out.
    locked_at       TIMESTAMPTZ,
    locked_by       VARCHAR(100),
    CONSTRAINT uk_audit_outbox_event_id UNIQUE (event_id),
    CONSTRAINT chk_audit_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT chk_audit_outbox_attempt_count CHECK (attempt_count >= 0)
);

COMMENT ON TABLE  audit_outbox                 IS 'Transactional outbox for audit events; written in the business transaction, drained by AuditOutboxDispatcher';
COMMENT ON COLUMN audit_outbox.event_id        IS 'Producer-assigned logical event identity; the idempotency key of the whole pipeline';
COMMENT ON COLUMN audit_outbox.last_error_code IS 'Error category only — never a raw driver message';

-- Claim query: WHERE status = ? AND next_attempt_at <= ? ORDER BY created_at
CREATE INDEX idx_audit_outbox_claim      ON audit_outbox(status, next_attempt_at, created_at);
-- Lease reclaim scan for rows stuck in PROCESSING
CREATE INDEX idx_audit_outbox_locked_at  ON audit_outbox(locked_at) WHERE status = 'PROCESSING';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. audit_logs — richer event model
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE audit_logs
    ADD COLUMN event_id     UUID,
    ADD COLUMN company_id   UUID,
    ADD COLUMN warehouse_id UUID,
    ADD COLUMN source       VARCHAR(30),
    ADD COLUMN reason_code  VARCHAR(100),
    ADD COLUMN http_method  VARCHAR(10),
    ADD COLUMN request_path VARCHAR(512),
    -- When the action happened, as opposed to created_at = when the row was materialised.
    -- Keeping both is what makes dispatcher lag measurable instead of invisible.
    ADD COLUMN occurred_at  TIMESTAMPTZ,
    ADD COLUMN metadata     JSONB,
    -- SHA-256 over the canonical event payload (AR-7 tamper evidence).
    ADD COLUMN payload_hash VARCHAR(64);

-- TraceIdFilter accepts an upstream X-Trace-Id of up to 64 characters, but this column was 32, so a
-- legitimate 40-character upstream id made the audit INSERT fail after the business had committed.
-- Widening the column is half the fix; AuditInputSanitizer capping the value is the other half —
-- neither side is allowed to depend on the other being correct.
ALTER TABLE audit_logs ALTER COLUMN trace_id TYPE VARCHAR(64);

-- Partial, because every historical row has event_id = NULL and NULLs are all distinct in Postgres
-- anyway; being explicit documents that the uniqueness claim only covers rows the new pipeline wrote.
CREATE UNIQUE INDEX uk_audit_logs_event_id ON audit_logs(event_id) WHERE event_id IS NOT NULL;

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_logs_status CHECK (status IN ('SUCCESS', 'FAILURE')),
    ADD CONSTRAINT chk_audit_logs_source CHECK (
        source IS NULL OR source IN ('HTTP', 'AUTH', 'SCHEDULED_JOB', 'MESSAGE', 'BATCH', 'SYSTEM'));

-- Read-API access paths (AR-8.4). audit_id is the tie-breaker that keeps pagination stable when
-- several events share a timestamp — without it, page 2 can repeat or skip a row from page 1.
CREATE INDEX idx_audit_occurred_at   ON audit_logs(occurred_at DESC, audit_id);
CREATE INDEX idx_audit_action_time   ON audit_logs(action, occurred_at DESC);
CREATE INDEX idx_audit_plant_time    ON audit_logs(plant_id, occurred_at DESC);
CREATE INDEX idx_audit_company_id    ON audit_logs(company_id);

COMMENT ON COLUMN audit_logs.event_id     IS 'Logical event identity from audit_outbox; NULL on rows written before V67';
COMMENT ON COLUMN audit_logs.occurred_at  IS 'When the action happened; created_at is when this row was materialised';
COMMENT ON COLUMN audit_logs.payload_hash IS 'SHA-256 of the canonical event payload (tamper evidence)';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. audit_log_entities — every object an event touched
--
-- One entity_type/entity_id pair on audit_logs cannot describe a command that connects two objects.
-- "PERMISSION_GRANTED" named the role and left the permission unrecorded, so the trail could say a
-- role changed but not what it gained. The primary target is dual-written to the legacy columns for
-- the whole compatibility window; only a later major phase may consider dropping them.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE audit_log_entities (
    audit_entity_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id        UUID         NOT NULL REFERENCES audit_logs(audit_id) ON DELETE CASCADE,
    relation        VARCHAR(20)  NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(255),
    entity_name     VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_audit_log_entities_relation CHECK (relation IN ('PRIMARY', 'RELATED'))
);

CREATE INDEX idx_audit_log_entities_audit_id ON audit_log_entities(audit_id);
CREATE INDEX idx_audit_log_entities_lookup   ON audit_log_entities(entity_type, entity_id, audit_id);

COMMENT ON TABLE audit_log_entities IS 'Objects touched by one audit event: exactly one PRIMARY, any number of RELATED';
