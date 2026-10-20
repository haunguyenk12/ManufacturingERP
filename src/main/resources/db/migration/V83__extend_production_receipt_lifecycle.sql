-- G9a: production receipt lifecycle and reads (FE_HANDOFF_GD_09 §1.3/§3.3, NEXT_PHASE_PLAN.md §16 D5/D6/D8).
--
--   * received_on: the business date of the receipt (plant-local), required on every new receipt. Existing rows
--     are backfilled from the instant the stock was received — approved_at, else posted_at (which the entity
--     stamps when the draft is created) — in the plant's time zone, then made NOT NULL: the same shape V77 used
--     for goods receipts.
--   * expires_on: optional shelf-life end of the output lot, passed to the lot at approval. Never backfilled.
--   * submitted_by / rejected_by / withdrawn_* / cancelled_*: who did each lifecycle step and why. CANCELLED was
--     already allowed by chk_production_receipts_status (V26) but nothing led there until now.
--   * PERM_PRODUCTION_RECEIPT_READ / PERM_PRODUCTION_QC_READ for ADMIN and MANAGER (D6). Reading still accepts
--     the action permissions, so nobody who can read receipts today loses that.

ALTER TABLE production_receipts
    ADD COLUMN received_on      DATE,
    ADD COLUMN expires_on       DATE,
    ADD COLUMN submitted_by     UUID,
    ADD COLUMN rejected_by      UUID,
    ADD COLUMN withdrawn_at     TIMESTAMPTZ,
    ADD COLUMN withdrawn_by     UUID,
    ADD COLUMN withdraw_reason  TEXT,
    ADD COLUMN cancelled_at     TIMESTAMPTZ,
    ADD COLUMN cancelled_by     UUID,
    ADD COLUMN cancel_reason    TEXT;

UPDATE production_receipts r
SET received_on = (COALESCE(r.approved_at, r.posted_at) AT TIME ZONE p.timezone)::date
FROM work_orders w
JOIN plants p ON p.plant_id = w.plant_id
WHERE w.work_order_id = r.work_order_id;

ALTER TABLE production_receipts ALTER COLUMN received_on SET NOT NULL;

-- V16 already allowed CANCELLED; any such row predates the column, and its last update is when it was cancelled.
UPDATE production_receipts SET cancelled_at = updated_at WHERE status = 'CANCELLED' AND cancelled_at IS NULL;

ALTER TABLE production_receipts
    ADD CONSTRAINT chk_production_receipts_expires_after_received
        CHECK (expires_on IS NULL OR expires_on > received_on),
    ADD CONSTRAINT chk_production_receipts_cancelled_has_time
        CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL);

CREATE INDEX idx_production_receipts_received_on ON production_receipts (received_on);

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_PRODUCTION_RECEIPT_READ', 'PRODUCTION_RECEIPT', 'READ', 'Read production receipts and their source'),
    ('PERM_PRODUCTION_QC_READ', 'PRODUCTION_QC', 'READ', 'Read approved production receipts and their QC decisions')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_PRODUCTION_RECEIPT_READ', 'PERM_PRODUCTION_QC_READ')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
