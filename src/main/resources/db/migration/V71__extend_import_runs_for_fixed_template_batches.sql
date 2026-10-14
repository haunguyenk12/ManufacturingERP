-- V71__extend_import_runs_for_fixed_template_batches.sql
-- G3 (FE_HANDOFF_GD_03_2026-10-12.md): the fixed-template, all-or-nothing import path
-- (/v1/data-import/batches) lives in the same tables as the mapped-profile path (/v1/import-runs).
--
-- Purely additive. Every existing row is a mapped-profile run, which is exactly what the DEFAULT on
-- `kind` says, so no row is reinterpreted. The other new columns stay NULL on those rows: they
-- describe things the old path never recorded, and inventing values for them would be fake history.

ALTER TABLE import_runs
    ADD COLUMN kind                   VARCHAR(20) NOT NULL DEFAULT 'MAPPED_PROFILE',
    -- Who uploaded the batch. History/detail of the new path are filtered on it (FE Q13).
    ADD COLUMN owner_user_id          UUID REFERENCES users (user_id),
    ADD COLUMN locale                 VARCHAR(5),
    ADD COLUMN template_version       VARCHAR(40),
    ADD COLUMN committed_at           TIMESTAMPTZ,
    -- Idempotency-Key of the upload, scoped to its owner. The existing idempotency_key column keeps
    -- meaning "key of the write step" (apply on the old path, commit on the new one).
    ADD COLUMN upload_idempotency_key VARCHAR(120),
    ADD COLUMN upload_payload_hash    VARCHAR(64);

ALTER TABLE import_runs
    ADD CONSTRAINT chk_import_runs_kind CHECK (kind IN ('MAPPED_PROFILE', 'FIXED_TEMPLATE'));

-- A fixed-template batch without an owner could never be listed or opened again by anyone.
ALTER TABLE import_runs
    ADD CONSTRAINT chk_import_runs_fixed_template_owner CHECK (
        kind <> 'FIXED_TEMPLATE' OR owner_user_id IS NOT NULL);

ALTER TABLE import_runs
    DROP CONSTRAINT chk_import_runs_status;

ALTER TABLE import_runs
    ADD CONSTRAINT chk_import_runs_status CHECK (status IN (
        'PARSING', 'PARSED', 'VALIDATING', 'VALIDATED', 'APPLYING',
        'APPLIED', 'PARTIALLY_APPLIED', 'FAILED', 'CANCELLED',
        'READY', 'INVALID', 'COMMITTED'));

-- Partial: rows without an upload key (every old-path run) are not constrained at all.
CREATE UNIQUE INDEX uk_import_runs_owner_upload_key
    ON import_runs (owner_user_id, upload_idempotency_key)
    WHERE upload_idempotency_key IS NOT NULL;

CREATE INDEX idx_import_runs_owner_company_created
    ON import_runs (owner_user_id, company_id, created_at DESC)
    WHERE kind = 'FIXED_TEMPLATE';

COMMENT ON COLUMN import_runs.kind IS
    'MAPPED_PROFILE = /v1/import-runs (per-row apply); FIXED_TEMPLATE = /v1/data-import/batches (atomic commit).';
COMMENT ON COLUMN import_runs.idempotency_key IS
    'Client Idempotency-Key of the write step: apply (MAPPED_PROFILE) or commit (FIXED_TEMPLATE).';
