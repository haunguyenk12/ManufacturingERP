-- V58__add_idempotency_to_mrp_runs.sql
-- Makes POST /planning-runs replay-safe.
--
-- The frontend sent the same Idempotency-Key three times with an identical payload and got three
-- runs (RUN-63033D7C, RUN-EA182AFB, RUN-4C77B225) — the header was silently ignored, because
-- nothing in module/planning ever read it, while best-practices.md A2 promised an MRP run honoured
-- it. Duplicate runs are not just noise: each one produces its own parallel set of supply
-- suggestions for the same demand, so converting "the" suggestion becomes ambiguous.
--
-- Same shape as stock_movements (V37): the key and a payload fingerprint live on the document row,
-- and the constraint is what actually enforces uniqueness. The replay lookup in MrpRunService must
-- stay in step with this constraint — a query narrower than the constraint returns a
-- non-deterministic row, a wider one lets the database reject a legitimate run (B69's lesson).
--
-- Both columns are NULLable with NO backfill: the header is optional, and every run created before
-- this migration was created without one. Postgres treats two NULLs as distinct in a UNIQUE
-- constraint, so any number of key-less runs keep coexisting — that is exactly what is wanted here,
-- and it is why the constraint can be a plain UNIQUE rather than a partial index.

ALTER TABLE mrp_runs
    ADD COLUMN idempotency_key VARCHAR(120),
    ADD COLUMN payload_hash    VARCHAR(64);

ALTER TABLE mrp_runs
    ADD CONSTRAINT uk_mrp_runs_idempotency_key UNIQUE (idempotency_key);

COMMENT ON COLUMN mrp_runs.idempotency_key IS
    'Client-supplied Idempotency-Key; NULL for runs submitted without the header.';
COMMENT ON COLUMN mrp_runs.payload_hash IS
    'SHA-256 of the run request, so replaying a key with a different body is a 409 instead of silently returning the first run.';
