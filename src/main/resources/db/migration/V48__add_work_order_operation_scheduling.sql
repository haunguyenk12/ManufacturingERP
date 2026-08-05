-- V48__add_work_order_operation_scheduling.sql
-- C2-8: static CRP + Capacity Board + Schedule Adjustment (NEXT_PHASE_PLAN.md §1).
--
-- work_center_id: nullable FK, alongside the existing work_center_code (display snapshot,
-- unchanged) — see module/workcenter/CLAUDE.md mục 4 for why this reverses the C2-6 "no FK"
-- decision. Historical rows (created before this migration) get NULL and are simply invisible to
-- the Capacity Board, same "not backfilled" shape as routing_operations.work_center_id (B_wc2).
--
-- planned_start_at / planned_end_at: generated once, at WorkOrder.release() (decision #1,
-- NEXT_PHASE_PLAN.md). Nullable — DRAFT/PLANNED/BLOCKED work orders have no schedule yet.
--
-- schedule_adjustment_reason: last-reason-wins, same shape as work_orders.block_reason /
-- work_orders.cancel_reason — not a full audit trail (the audit log already captures that).

ALTER TABLE work_order_operations
    ADD COLUMN work_center_id UUID REFERENCES work_centers(work_center_id),
    ADD COLUMN planned_start_at TIMESTAMPTZ,
    ADD COLUMN planned_end_at TIMESTAMPTZ,
    ADD COLUMN schedule_adjustment_reason TEXT;

CREATE INDEX idx_work_order_operations_work_center_id ON work_order_operations(work_center_id);
CREATE INDEX idx_work_order_operations_planned_start_at ON work_order_operations(planned_start_at);
