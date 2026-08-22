-- Align databases that ran the original V59 with the current data-import model.
-- IF NOT EXISTS keeps this migration compatible with clean databases where the
-- current V59 already contains these columns.

ALTER TABLE import_runs
    ADD COLUMN IF NOT EXISTS plant_id UUID REFERENCES plants (plant_id),
    ADD COLUMN IF NOT EXISTS warehouse_id UUID REFERENCES warehouses (warehouse_id),
    ADD COLUMN IF NOT EXISTS file_sha256 VARCHAR(64);

-- A legacy row cannot recover the source file hash. Give it a stable, unique
-- compatibility value so the new NOT NULL contract can still be enforced.
UPDATE import_runs
SET file_sha256 = md5(import_run_id::text) || md5(import_run_id::text || ':legacy')
WHERE file_sha256 IS NULL;

ALTER TABLE import_runs
    ALTER COLUMN file_sha256 SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'PARSING';

ALTER TABLE import_runs
    DROP CONSTRAINT IF EXISTS chk_import_runs_status;

ALTER TABLE import_runs
    ADD CONSTRAINT chk_import_runs_status CHECK (status IN (
        'PARSING', 'PARSED', 'VALIDATING', 'VALIDATED', 'APPLYING',
        'APPLIED', 'PARTIALLY_APPLIED', 'FAILED', 'CANCELLED'));

ALTER TABLE import_rows
    DROP CONSTRAINT IF EXISTS chk_import_rows_status;

ALTER TABLE import_rows
    ADD CONSTRAINT chk_import_rows_status CHECK (status IN (
        'PENDING', 'VALID', 'ERROR', 'APPLIED', 'FAILED', 'SKIPPED'));
