-- V39 (F8): the only schema change of the phase. Everything else F8 adds is DTO/mapper plumbing
-- over columns that already exist.
--
-- 1. work_orders.execution_started_at (spec §5.3)
--    "Lần post đầu tiên đặt executionStartedAt nếu chưa có." The counterpart completed_at already
--    exists and is set by WorkOrder.complete(); this is the missing opening bracket. Set inside
--    WorkOrder.reportProduction() — it is a state transition of the work order, so it belongs on the
--    entity next to complete(), not in a service.
--
--    Deliberately NOT paired with an execution_completed_at column: WorkOrder.complete(now) has
--    exactly one call site (reportProduction) and invariant B53 pins that, so completed_at already
--    IS the execution-completed timestamp. A second column holding the same instant is a
--    denormalisation that goes wrong the first time somebody adds a call site.
--
-- 2. Planning lineage (3 columns, spec §3.3 "Nguồn gốc")
--    Plain nullable columns, deliberately NOT foreign keys to the planning tables. Same reasoning as
--    WorkOrderDemandAllocation.salesOrderLineId (module/workorder/CLAUDE.md): sales → planning →
--    workorder already exists, so a typed @ManyToOne back into planning would close a compile-time
--    dependency cycle. planning_run_code is denormalised on purpose, exactly like
--    source_routing_code (B49): it is a snapshot of what the run was called when the work order was
--    created, not a live lookup.
--
-- 3. mrp_runs.gross_demand_quantity (spec §2.4 Run header)
--    Counted in place from MrpCalculationResult — the sum of grossRequiredQuantity over level-0
--    requirements — exactly like the four summary cells V36 added. Not re-queried on read.
--
-- Historical data: work orders and runs created before F8 carry NULL / 0. Following the V38
-- precedent, NULL here means "created before F8", NOT "has no planning origin". A work order
-- created manually will also carry NULL, and that is correct — it genuinely has no MRP lineage.

ALTER TABLE work_orders
    ADD COLUMN execution_started_at TIMESTAMPTZ,
    ADD COLUMN planning_run_id      UUID,
    ADD COLUMN planning_run_code    VARCHAR(40),
    ADD COLUMN planning_proposal_id UUID;

-- Filtering work orders by the run that produced them is the "what did this MRP run actually
-- create?" question of spec §2.4; without an index it is a sequential scan over every work order.
CREATE INDEX idx_work_orders_planning_run_id ON work_orders (planning_run_id);

ALTER TABLE mrp_runs
    ADD COLUMN gross_demand_quantity NUMERIC(19,6) NOT NULL DEFAULT 0;

ALTER TABLE mrp_runs DROP CONSTRAINT chk_mrp_runs_totals;
ALTER TABLE mrp_runs ADD CONSTRAINT chk_mrp_runs_totals CHECK (
    total_demand_lines >= 0
    AND total_requirement_lines >= 0
    AND total_suggestion_lines >= 0
    AND shortage_lines >= 0
    AND planned_work_orders >= 0
    AND planned_purchase_recommendations >= 0
    AND blocked_proposals >= 0
    AND gross_demand_quantity >= 0
);
