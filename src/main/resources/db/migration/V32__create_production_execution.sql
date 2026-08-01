-- V32__create_production_execution.sql
-- Phase F5 – reshape Planning + Work Order + Production Execution.
--
-- This migration carries the semantic inversion described in CLAUDE.md §0.5: the shop floor,
-- not the production receipt, is what makes a work order progress.
--   * work_orders gains cumulative actual_good/scrap/rework quantities, fed by production_executions.
--   * completed_quantity keeps its meaning "how much has been receipted into stock" — it is NOT
--     renamed, so no existing row changes meaning (NEXT_PHASE_PLAN F5 §1.2 item 6).
--   * production_receipts are now capped by (actual_good - receipted), not by planned quantity.

-- ── Work order: PLANNED status ─────────────────────────────────────────────
-- Spec §3.1. A work order that has been scheduled but whose material is not yet being reserved.
ALTER TABLE work_orders DROP CONSTRAINT chk_work_orders_status;
ALTER TABLE work_orders ADD CONSTRAINT chk_work_orders_status
    CHECK (status IN ('DRAFT','PLANNED','BLOCKED','RELEASED','IN_PROGRESS','COMPLETED','CANCELLED'));

-- ── Work order: cumulative shop-floor quantities ───────────────────────────
ALTER TABLE work_orders
    ADD COLUMN actual_good_quantity   NUMERIC(19,6) NOT NULL DEFAULT 0,
    ADD COLUMN actual_scrap_quantity  NUMERIC(19,6) NOT NULL DEFAULT 0,
    ADD COLUMN actual_rework_quantity NUMERIC(19,6) NOT NULL DEFAULT 0;

-- Rows written before F5 were advanced by receipt approval, so everything already receipted was,
-- by definition, produced. Backfilling keeps availableToReceipt = actual_good - completed = 0,
-- i.e. a pre-F5 work order needs a shop-floor report before it can be receipted again.
UPDATE work_orders SET actual_good_quantity = completed_quantity WHERE completed_quantity > 0;

ALTER TABLE work_orders
    ADD CONSTRAINT chk_work_orders_actual_good
        CHECK (actual_good_quantity >= 0 AND actual_good_quantity <= planned_quantity),
    ADD CONSTRAINT chk_work_orders_actual_scrap  CHECK (actual_scrap_quantity >= 0),
    ADD CONSTRAINT chk_work_orders_actual_rework CHECK (actual_rework_quantity >= 0),
    -- A receipt can never claim more than the shop floor reported as good (invariant B16, rewritten).
    ADD CONSTRAINT chk_work_orders_completed_within_good
        CHECK (completed_quantity <= actual_good_quantity);

-- ── Work order operations: routing snapshot, one row per operation ─────────
-- F4 deliberately deferred this table so it could be designed together with production_executions
-- (NEXT_PHASE_PLAN F5 §1.4 item 12). Same snapshot contract as work_orders.source_routing_* :
-- the values are COPIED, so revising the routing master leaves released work orders untouched (B49).
CREATE TABLE work_order_operations (
    work_order_operation_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id              UUID          NOT NULL REFERENCES work_orders(work_order_id) ON DELETE CASCADE,
    -- Provenance only. Never read through to fetch name/minutes: that would resurrect live master data.
    source_routing_operation_id UUID,
    sequence                   INTEGER       NOT NULL,
    name                       VARCHAR(255)  NOT NULL,
    work_center_code           VARCHAR(100)  NOT NULL,
    setup_minutes              NUMERIC(19,6) NOT NULL DEFAULT 0,
    run_minutes_per_unit       NUMERIC(19,6) NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                 UUID,
    updated_by                 UUID,
    version                    BIGINT        DEFAULT 0,
    CONSTRAINT uk_work_order_operations_sequence UNIQUE (work_order_id, sequence),
    CONSTRAINT chk_work_order_operations_sequence CHECK (sequence > 0),
    CONSTRAINT chk_work_order_operations_setup CHECK (setup_minutes >= 0),
    CONSTRAINT chk_work_order_operations_run CHECK (run_minutes_per_unit >= 0)
);

CREATE INDEX idx_work_order_operations_work_order_id ON work_order_operations(work_order_id);

-- ── Production execution: the shop-floor report ────────────────────────────
-- Lives in module/workorder for the same reason quality_dispositions does (F2): it belongs to the
-- Work Order aggregate, its endpoints hang off /work-orders/{id}.
CREATE TABLE production_executions (
    production_execution_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id           UUID          NOT NULL REFERENCES work_orders(work_order_id),
    work_order_operation_id UUID          REFERENCES work_order_operations(work_order_operation_id),
    good_quantity           NUMERIC(19,6) NOT NULL DEFAULT 0,
    scrap_quantity          NUMERIC(19,6) NOT NULL DEFAULT 0,
    rework_quantity         NUMERIC(19,6) NOT NULL DEFAULT 0,
    actual_started_at       TIMESTAMPTZ,
    actual_ended_at         TIMESTAMPTZ,
    operator_user_id        UUID,
    -- Business traceability, not the debug header: see .claude/rules/error-handling.md §5.1.
    trace_id                VARCHAR(64),
    idempotency_key         VARCHAR(120)  NOT NULL,
    payload_hash            VARCHAR(64),
    notes                   TEXT,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT        DEFAULT 0,
    CONSTRAINT uk_production_executions_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_production_executions_good   CHECK (good_quantity >= 0),
    CONSTRAINT chk_production_executions_scrap  CHECK (scrap_quantity >= 0),
    CONSTRAINT chk_production_executions_rework CHECK (rework_quantity >= 0),
    CONSTRAINT chk_production_executions_total
        CHECK (good_quantity + scrap_quantity + rework_quantity > 0)
);

CREATE INDEX idx_production_executions_work_order_id ON production_executions(work_order_id);
CREATE INDEX idx_production_executions_operation_id ON production_executions(work_order_operation_id);
CREATE INDEX idx_production_executions_created_at ON production_executions(created_at DESC);

-- ── WIP ledger keeps recording everything ──────────────────────────────────
-- stage_code stays for work orders created without a routing; when the caller reports against an
-- operation the FK is the authoritative link and stage_code is derived from it.
ALTER TABLE wip_transactions
    ADD COLUMN work_order_operation_id UUID REFERENCES work_order_operations(work_order_operation_id);

CREATE INDEX idx_wip_transactions_operation_id ON wip_transactions(work_order_operation_id);

-- OUTPUT_COMPLETED now means "the shop floor produced it"; OUTPUT_RECEIPTED means "a receipt moved
-- it into stock". Splitting them keeps WorkOrderVarianceService able to tell production from
-- warehousing now that the two are no longer the same event.
ALTER TABLE wip_transactions DROP CONSTRAINT chk_wip_transactions_type;
ALTER TABLE wip_transactions ADD CONSTRAINT chk_wip_transactions_type
    CHECK (transaction_type IN ('START', 'MATERIAL_ISSUED', 'OUTPUT_COMPLETED', 'OUTPUT_RECEIPTED',
                                'SCRAP_REPORTED', 'REWORK_REPORTED'));

-- ── Business traceId on the documents that need to be traced ───────────────
ALTER TABLE material_issues     ADD COLUMN trace_id VARCHAR(64);
ALTER TABLE stock_movements     ADD COLUMN trace_id VARCHAR(64);
ALTER TABLE production_receipts ADD COLUMN trace_id VARCHAR(64);

-- Every WIP trace that fed this receipt, oldest first, comma separated. Stored denormalised rather
-- than as a join table: it is written once at approval and only ever read back whole (spec §6.4).
ALTER TABLE production_receipts ADD COLUMN source_wip_trace_ids TEXT;

-- Document number (spec §6.4). Nullable because pre-F5 receipts have none and back-filling a
-- human-facing sequence retroactively would invent numbers that never existed on paper.
ALTER TABLE production_receipts ADD COLUMN code VARCHAR(40);
CREATE UNIQUE INDEX uk_production_receipts_code ON production_receipts(code) WHERE code IS NOT NULL;

CREATE INDEX idx_material_issues_trace_id ON material_issues(trace_id);
CREATE INDEX idx_stock_movements_trace_id ON stock_movements(trace_id);
CREATE INDEX idx_production_receipts_trace_id ON production_receipts(trace_id);
