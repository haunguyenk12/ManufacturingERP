-- P6 — WO Close/Reconcile: terminal CLOSED status, reachable only from COMPLETED.
ALTER TABLE work_orders DROP CONSTRAINT chk_work_orders_status;
ALTER TABLE work_orders ADD CONSTRAINT chk_work_orders_status
    CHECK (status IN ('DRAFT','PLANNED','BLOCKED','RELEASED','IN_PROGRESS','COMPLETED','CANCELLED','CLOSED'));

ALTER TABLE work_orders ADD COLUMN closed_at TIMESTAMPTZ;
