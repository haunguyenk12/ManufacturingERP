-- V38 (F7): Record WHY a work order was cancelled.
--
-- Spec §3.2 lists POST /work-orders/{id}/cancel as "Nếu MVP mở action cancel; cần reason", but the
-- endpoint took no body and the table had nowhere to put one: work_orders already had cancelled_at
-- and block_reason, yet cancelling threw the reason away. A cancelled production order with no
-- recorded cause is exactly the row an auditor asks about.
--
-- TEXT, matching block_reason (V-earlier) rather than a bounded VARCHAR — these are free-form
-- operator explanations and the column is never indexed or filtered on.
--
-- Deliberately NULLABLE. Work orders cancelled before this migration have no reason to backfill,
-- and inventing one ("migrated", "unknown") would put a fabricated audit trail into history. The
-- "reason is mandatory" rule is enforced going forward, at the service layer, where it can return
-- APPROVAL_REASON_REQUIRED (400) instead of a constraint violation the client cannot act on.
-- Consequence to remember: cancel_reason IS NULL means "cancelled before F7", not "no reason given".

ALTER TABLE work_orders
    ADD COLUMN cancel_reason TEXT;
