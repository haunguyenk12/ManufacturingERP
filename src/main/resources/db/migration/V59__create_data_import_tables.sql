-- V59__create_data_import_tables.sql
-- Spreadsheet master-data import: the two-phase (validate -> apply) framework.
--
-- Why this shape, given that no customer sample file exists yet:
--
--   import_rows.raw_cells is JSONB keyed by whatever header text the customer typed. A column the
--   system has never heard of is STORED, not rejected — so the schema cannot be invalidated by a
--   file we have not seen. What the sample file will eventually decide is the CONTENT of one
--   import_profiles row, not the shape of any table here.
--
--   import_profiles.mappings holds "source header -> target field + transforms". Keeping that in
--   data rather than in Java is the whole point: a second customer with different column names is
--   an INSERT, not a code change and not a new class.
--
-- import_runs follows mrp_runs (V36 + V58) deliberately: a status, summary counters, an optional
-- Idempotency-Key with a payload fingerprint. Same reasoning as B69/B117 — the UNIQUE constraint is
-- what actually prevents a duplicate run; the replay lookup only decides how to answer one.

-- ── Mapping profiles ───────────────────────────────────────────────────────
CREATE TABLE import_profiles (
    profile_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 VARCHAR(100) NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    target_type          VARCHAR(40)  NOT NULL,
    -- NULL = a profile usable by every company (the shipped default for our own template).
    company_id           UUID         REFERENCES companies (company_id),
    -- NULL = first sheet. 0-based row indexes, matching POI, not the 1-based numbers Excel shows.
    sheet_name           VARCHAR(255),
    header_row_index     INT          NOT NULL DEFAULT 0,
    first_data_row_index INT          NOT NULL DEFAULT 1,
    mappings             JSONB        NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT       DEFAULT 0,
    CONSTRAINT uk_import_profiles_code UNIQUE (code),
    CONSTRAINT chk_import_profiles_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_import_profiles_rows CHECK (
        header_row_index >= 0 AND first_data_row_index > header_row_index)
);

CREATE INDEX idx_import_profiles_target_company ON import_profiles (target_type, company_id);

-- ── Runs ───────────────────────────────────────────────────────────────────
CREATE TABLE import_runs (
    import_run_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(40)  NOT NULL,
    target_type       VARCHAR(40)  NOT NULL,
    profile_id        UUID         REFERENCES import_profiles (profile_id),
    company_id        UUID         NOT NULL REFERENCES companies (company_id),
    plant_id          UUID         REFERENCES plants (plant_id),
    warehouse_id      UUID         REFERENCES warehouses (warehouse_id),
    original_filename VARCHAR(255) NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    file_sha256       VARCHAR(64)  NOT NULL,
    -- Every header found in the file, including ones no mapping refers to. This is what lets the
    -- frontend show "these columns were ignored" instead of silently dropping them.
    detected_headers  JSONB        NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PARSING',
    total_rows        INT          NOT NULL DEFAULT 0,
    valid_rows        INT          NOT NULL DEFAULT 0,
    error_rows        INT          NOT NULL DEFAULT 0,
    applied_rows      INT          NOT NULL DEFAULT 0,
    failed_rows       INT          NOT NULL DEFAULT 0,
    parsed_at         TIMESTAMPTZ,
    validated_at      TIMESTAMPTZ,
    applied_at        TIMESTAMPTZ,
    error_message     TEXT,
    idempotency_key   VARCHAR(120),
    payload_hash      VARCHAR(64),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       DEFAULT 0,
    CONSTRAINT uk_import_runs_code UNIQUE (code),
    -- NULLable with no backfill, exactly like V58: the header is optional, and Postgres treats two
    -- NULLs as distinct, so any number of key-less runs coexist.
    CONSTRAINT uk_import_runs_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_import_runs_status CHECK (status IN (
        'PARSING', 'PARSED', 'VALIDATING', 'VALIDATED', 'APPLYING',
        'APPLIED', 'PARTIALLY_APPLIED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_import_runs_totals CHECK (
        total_rows >= 0 AND valid_rows >= 0 AND error_rows >= 0
        AND applied_rows >= 0 AND failed_rows >= 0)
);

CREATE INDEX idx_import_runs_company_target_created
    ON import_runs (company_id, target_type, created_at DESC);

-- ── Staging rows ───────────────────────────────────────────────────────────
CREATE TABLE import_rows (
    import_row_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_run_id     UUID        NOT NULL REFERENCES import_runs (import_run_id) ON DELETE CASCADE,
    -- 1-based, as the user sees it in Excel, so an error message can name a row they can navigate to.
    row_number        INT         NOT NULL,
    raw_cells         JSONB       NOT NULL,
    mapped_values     JSONB,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    errors            JSONB,
    created_entity_id UUID,
    CONSTRAINT uk_import_rows_run_row UNIQUE (import_run_id, row_number),
    CONSTRAINT chk_import_rows_status CHECK (status IN (
        'PENDING', 'VALID', 'ERROR', 'APPLIED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_import_rows_run_status ON import_rows (import_run_id, status);

COMMENT ON COLUMN import_rows.raw_cells IS
    'Cells exactly as Excel displayed them, keyed by source header. Columns with no mapping are kept.';
COMMENT ON COLUMN import_rows.errors IS
    'Array of {column, targetField, code, message}; NULL until the row has been validated.';
COMMENT ON COLUMN import_runs.idempotency_key IS
    'Client-supplied Idempotency-Key on apply; NULL when the header was absent.';
