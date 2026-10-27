-- G11b (FE handoff GĐ11): production cost runs. A run is a calculation of the cost of one work order saved with the
-- revision of its sources (an opaque hash, FE Q31) — never a stock, value or work order status change (FE R16). A
-- PREVIEW run becomes FINALIZED only by the atomic finalize-and-close command (G11c), which re-calculates on the
-- server and stores the snapshot at close (FE R26). The snapshot is the full calculation as JSON; the totals are
-- columns so lists do not parse it.

CREATE TABLE production_cost_runs (
    run_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_no             VARCHAR(40)    NOT NULL,
    work_order_id      UUID           NOT NULL REFERENCES work_orders (work_order_id),
    plant_id           UUID           NOT NULL REFERENCES plants (plant_id),
    state              VARCHAR(20)    NOT NULL DEFAULT 'PREVIEW',
    run_version        INTEGER        NOT NULL DEFAULT 1,
    source_version     VARCHAR(80)    NOT NULL,
    work_order_version BIGINT         NOT NULL,
    planned_total      NUMERIC(19, 6),
    actual_total       NUMERIC(19, 6),
    snapshot           JSONB          NOT NULL,
    finalized_at       TIMESTAMPTZ,
    finalized_by       UUID,
    close_reason       TEXT,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT         DEFAULT 0,
    CONSTRAINT uk_production_cost_runs_run_no UNIQUE (run_no),
    CONSTRAINT chk_production_cost_runs_state CHECK (state IN ('PREVIEW', 'FINALIZED')),
    CONSTRAINT chk_production_cost_runs_run_version CHECK (run_version > 0),
    CONSTRAINT chk_production_cost_runs_finalized CHECK (
        state <> 'FINALIZED' OR (finalized_at IS NOT NULL AND close_reason IS NOT NULL))
);

CREATE INDEX idx_production_cost_runs_work_order ON production_cost_runs (work_order_id, created_at);
-- At most one finalized run per work order.
CREATE UNIQUE INDEX uk_production_cost_runs_finalized ON production_cost_runs (work_order_id) WHERE state = 'FINALIZED';
