-- V23__create_work_order_approval_gates.sql
-- Phase P1 – Approval Workflow & Business Gates.
-- Opens the schema for the three business gates:
--   1a) Release requires full material reservation  -> work_orders.BLOCKED
--   1b) Approved over-issue beyond BOM requirement  -> material_issue_lines.over_issue
--   1c) Production receipt approval                 -> production_receipts.PENDING_APPROVAL

-- ── Gate 1a: BLOCKED status ────────────────────────────────────────────────
ALTER TABLE work_orders DROP CONSTRAINT chk_work_orders_status;
ALTER TABLE work_orders ADD CONSTRAINT chk_work_orders_status
    CHECK (status IN ('DRAFT','BLOCKED','RELEASED','IN_PROGRESS','COMPLETED','CANCELLED'));
ALTER TABLE work_orders
    ADD COLUMN blocked_at   TIMESTAMPTZ,
    ADD COLUMN block_reason TEXT;

-- ── Gate 1b: cho phép over-issue đã duyệt ──────────────────────────────────
ALTER TABLE work_order_component_lines DROP CONSTRAINT chk_work_order_component_issued;
ALTER TABLE work_order_component_lines ADD CONSTRAINT chk_work_order_component_issued
    CHECK (issued_quantity >= 0);
ALTER TABLE material_issue_lines
    ADD COLUMN over_issue      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN override_reason TEXT;

-- ── Gate 1c: duyệt production receipt ──────────────────────────────────────
ALTER TABLE production_receipts DROP CONSTRAINT chk_production_receipts_status;
ALTER TABLE production_receipts ADD CONSTRAINT chk_production_receipts_status
    CHECK (status IN ('PENDING_APPROVAL','POSTED','REJECTED','CANCELLED'));
ALTER TABLE production_receipts
    ADD COLUMN approved_at   TIMESTAMPTZ,
    ADD COLUMN approved_by   UUID,
    ADD COLUMN rejected_at   TIMESTAMPTZ,
    ADD COLUMN reject_reason TEXT;

ALTER TABLE production_receipt_lines ALTER COLUMN stock_movement_id DROP NOT NULL;
ALTER TABLE production_receipt_lines
    ADD COLUMN requested_lot_code VARCHAR(120),
    ADD COLUMN reason             TEXT;

CREATE INDEX idx_material_issue_lines_over_issue
    ON material_issue_lines(over_issue) WHERE over_issue = TRUE;
