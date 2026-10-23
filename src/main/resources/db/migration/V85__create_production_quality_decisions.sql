-- G9c: production QC by quantity, several decisions per receipt (FE_HANDOFF_GD_09 R09–R11, Q15–Q21, Q24, Q29;
-- NEXT_PHASE_PLAN.md §16 D1/D2/D4).
--
--   * production_quality_decisions: one immutable row per QC decision on an APPROVED production receipt line —
--     the production twin of purchase_quality_decisions (V78). quality_dispositions (one row per lot, whole-lot
--     verdict, lot_id NOT NULL) is kept untouched as history (D1).
--   * production_receipt_lines.qc_accepted_quantity / qc_rejected_quantity: running sums of the decisions, so
--     pending = quantity - accepted - rejected is one column read, guarded by a CHECK.
--
-- Data: receipts already decided through the whole-receipt QC endpoint (qc_result set) get their line totals and
-- one decision row from the columns that recorded that verdict — qc_result, qc_reason, qc_at, qc_by — so the
-- decision ledger is the single source from now on. Nothing is derived that was not recorded: the decision row
-- names the receipt's own lot (whole-lot status change) and no split movement, because none happened.

ALTER TABLE production_receipt_lines
    ADD COLUMN qc_accepted_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    ADD COLUMN qc_rejected_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0;

UPDATE production_receipt_lines l
SET qc_accepted_quantity = CASE WHEN r.qc_result = 'AVAILABLE' THEN l.quantity ELSE 0 END,
    qc_rejected_quantity = CASE WHEN r.qc_result = 'REJECTED' THEN l.quantity ELSE 0 END
FROM production_receipts r
WHERE r.receipt_id = l.receipt_id
  AND r.qc_result IS NOT NULL;

ALTER TABLE production_receipt_lines
    ADD CONSTRAINT chk_production_receipt_lines_qc_non_negative
        CHECK (qc_accepted_quantity >= 0 AND qc_rejected_quantity >= 0),
    ADD CONSTRAINT chk_production_receipt_lines_qc_within_quantity
        CHECK (qc_accepted_quantity + qc_rejected_quantity <= quantity);

CREATE TABLE production_quality_decisions (
    decision_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id             UUID           NOT NULL REFERENCES production_receipts (receipt_id),
    receipt_line_id        UUID           NOT NULL REFERENCES production_receipt_lines (receipt_line_id),
    accepted_quantity      NUMERIC(19, 6) NOT NULL,
    rejected_quantity      NUMERIC(19, 6) NOT NULL,
    reason                 TEXT,
    accepted_lot_id        UUID REFERENCES inventory_lots (lot_id),
    rejected_lot_id        UUID REFERENCES inventory_lots (lot_id),
    accepted_movement_id   UUID REFERENCES stock_movements (movement_id),
    rejected_movement_id   UUID REFERENCES stock_movements (movement_id),
    decided_at             TIMESTAMPTZ    NOT NULL,
    decided_by             UUID,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    created_by             UUID,
    updated_by             UUID,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_production_qc_quantities
        CHECK (accepted_quantity >= 0 AND rejected_quantity >= 0 AND accepted_quantity + rejected_quantity > 0),
    CONSTRAINT chk_production_qc_reject_reason
        CHECK (rejected_quantity = 0 OR (reason IS NOT NULL AND btrim(reason) <> ''))
);

CREATE INDEX idx_production_qc_receipt_decided_at ON production_quality_decisions (receipt_id, decided_at);

INSERT INTO production_quality_decisions (receipt_id, receipt_line_id, accepted_quantity, rejected_quantity, reason,
                                          accepted_lot_id, rejected_lot_id, decided_at, decided_by,
                                          created_at, updated_at, created_by, updated_by)
SELECT r.receipt_id,
       l.receipt_line_id,
       l.qc_accepted_quantity,
       l.qc_rejected_quantity,
       r.qc_reason,
       CASE WHEN r.qc_result = 'AVAILABLE' THEN l.lot_id END,
       CASE WHEN r.qc_result = 'REJECTED' THEN l.lot_id END,
       COALESCE(r.qc_at, r.updated_at),
       r.qc_by,
       COALESCE(r.qc_at, r.updated_at),
       COALESCE(r.qc_at, r.updated_at),
       r.qc_by,
       r.qc_by
FROM production_receipts r
JOIN production_receipt_lines l ON l.receipt_id = r.receipt_id
WHERE r.qc_result IS NOT NULL
  AND l.quantity > 0;
