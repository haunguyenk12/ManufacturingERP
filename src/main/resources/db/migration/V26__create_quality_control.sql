-- V26__create_quality_control.sql
-- Phase F2 – Production Receipt lifecycle + QC disposition (spec §6).
--
-- Three schema moves:
--   1) production_receipts gains DRAFT and renames POSTED -> APPROVED (breaking, data migrated below)
--   2) quality_dispositions records the per-lot QC decision
--   3) stock_movements accepts LOT_STATUS_CHANGE / direction NONE for the QC ledger entry

-- ── 1. Receipt lifecycle: DRAFT -> PENDING_APPROVAL -> APPROVED ────────────
-- Existing rows carry 'POSTED', which no longer exists in the enum. The CHECK constraint is
-- dropped first so the data update cannot fail against the old definition.
ALTER TABLE production_receipts DROP CONSTRAINT chk_production_receipts_status;

UPDATE production_receipts SET status = 'APPROVED' WHERE status = 'POSTED';

-- The column default still said 'POSTED' (V16), which the new constraint would reject.
ALTER TABLE production_receipts ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE production_receipts ADD CONSTRAINT chk_production_receipts_status
    CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','CANCELLED'));

ALTER TABLE production_receipts
    ADD COLUMN submitted_at TIMESTAMPTZ,
    ADD COLUMN qc_result    VARCHAR(20),
    ADD COLUMN qc_reason    TEXT,
    ADD COLUMN qc_at        TIMESTAMPTZ,
    ADD COLUMN qc_by        UUID;

-- Denormalised summary of quality_dispositions so the receipt list endpoint renders the QC
-- outcome without loading a collection per row.
ALTER TABLE production_receipts ADD CONSTRAINT chk_production_receipts_qc_result
    CHECK (qc_result IS NULL OR qc_result IN ('AVAILABLE','REJECTED'));

-- ── 2. QC decision, one row per output lot ─────────────────────────────────
CREATE TABLE quality_dispositions (
    disposition_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id     UUID         NOT NULL REFERENCES production_receipts(receipt_id) ON DELETE CASCADE,
    lot_id         UUID         NOT NULL REFERENCES inventory_lots(lot_id),
    result         VARCHAR(20)  NOT NULL,
    reason         TEXT         NOT NULL,
    decided_by     UUID,
    decided_at     TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT       DEFAULT 0,
    CONSTRAINT uk_quality_dispositions_receipt_lot UNIQUE (receipt_id, lot_id),
    CONSTRAINT chk_quality_dispositions_result CHECK (result IN ('AVAILABLE','REJECTED'))
);

CREATE INDEX idx_quality_dispositions_receipt_id ON quality_dispositions(receipt_id);
CREATE INDEX idx_quality_dispositions_lot_id     ON quality_dispositions(lot_id);

-- ── 3. Ledger entry for the lot status change ──────────────────────────────
-- LOT_STATUS_CHANGE never moves quantity, hence direction NONE: summing the ledger by direction
-- still reconciles against stock_balances.
--
-- REVERSAL is added in the same statement: MovementType.REVERSAL has existed since the goods
-- receipt cancel feature but was never added to this constraint, so every cancel would have been
-- rejected by the database. Fixed here rather than re-shipping a constraint already known wrong.
ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_type
    CHECK (movement_type IN ('RECEIVE','ISSUE','ADJUST_IN','ADJUST_OUT','REVERSAL','LOT_STATUS_CHANGE'));

ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_direction;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_direction
    CHECK (direction IN ('IN','OUT','NONE'));
