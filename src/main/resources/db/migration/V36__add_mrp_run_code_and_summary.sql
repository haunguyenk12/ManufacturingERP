-- D4.3 / debt #15: the Run header of spec §2.4 needs a human-facing code plus four summary cells.
-- The summary counters are a cache of numbers the run transaction already produced; they are not
-- derived on read, so existing rows keep the 0 default (their requirement/suggestion rows are still
-- queryable if a planner needs the real numbers of a historical run).

ALTER TABLE mrp_runs ADD COLUMN code VARCHAR(40);
ALTER TABLE mrp_runs ADD COLUMN shortage_lines                   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE mrp_runs ADD COLUMN planned_work_orders              INTEGER NOT NULL DEFAULT 0;
ALTER TABLE mrp_runs ADD COLUMN planned_purchase_recommendations INTEGER NOT NULL DEFAULT 0;
ALTER TABLE mrp_runs ADD COLUMN blocked_proposals                INTEGER NOT NULL DEFAULT 0;

-- Must stay identical to MrpRun.assignCode() so a backfilled code and a freshly generated one for
-- the same id are the same string.
UPDATE mrp_runs
SET code = 'RUN-' || upper(substring(mrp_run_id::text, 1, 8))
WHERE code IS NULL;

ALTER TABLE mrp_runs ALTER COLUMN code SET NOT NULL;

CREATE UNIQUE INDEX uk_mrp_runs_code ON mrp_runs (code);

ALTER TABLE mrp_runs DROP CONSTRAINT chk_mrp_runs_totals;
ALTER TABLE mrp_runs ADD CONSTRAINT chk_mrp_runs_totals CHECK (
    total_demand_lines >= 0
    AND total_requirement_lines >= 0
    AND total_suggestion_lines >= 0
    AND shortage_lines >= 0
    AND planned_work_orders >= 0
    AND planned_purchase_recommendations >= 0
    AND blocked_proposals >= 0
);
