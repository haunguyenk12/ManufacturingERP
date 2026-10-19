-- G7c: purchase QC by quantity (FE_HANDOFF_GD_07 §3.3, NEXT_PHASE_PLAN.md §14 D5–D7).
--
--   * purchase_quality_decisions: one immutable row per QC decision on a posted, on-hold goods
--     receipt line (D5 — not the production QualityDisposition table, whose lot_id is NOT NULL and
--     whose meaning is "one row per lot of a production receipt").
--   * goods_receipt_lines.accepted_quantity / rejected_quantity: running sums of the decisions, so
--     pending = received - accepted - rejected is one column read, guarded by a CHECK.
--   * inventory_lots.parent_lot_id + source_reference_*: the child lots a tracked decision splits off
--     the received lot (D6 — lineage only, not the full genealogy of P9).
--   * stock_movements: LOT_SPLIT_OUT / LOT_SPLIT_IN for the parent -> child move. On-hand is unchanged.
--   * PERM_PURCHASE_QC_READ / _MANAGE for ADMIN and MANAGER (D9, FE Q24).
--
-- Data: nothing is backfilled. Receipts posted before G7b never went on hold (quality_held = false)
-- and never enter the QC queue.

ALTER TABLE goods_receipt_lines
    ADD COLUMN accepted_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    ADD COLUMN rejected_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0;

ALTER TABLE goods_receipt_lines
    ADD CONSTRAINT chk_gr_lines_qc_non_negative CHECK (accepted_quantity >= 0 AND rejected_quantity >= 0),
    ADD CONSTRAINT chk_gr_lines_qc_within_received
        CHECK (accepted_quantity + rejected_quantity <= received_quantity);

ALTER TABLE inventory_lots
    ADD COLUMN parent_lot_id UUID REFERENCES inventory_lots (lot_id),
    ADD COLUMN source_reference_type VARCHAR(40),
    ADD COLUMN source_reference_id UUID;

CREATE INDEX idx_inventory_lots_parent_lot_id ON inventory_lots (parent_lot_id);

-- coding-rules.md §11.3: restate the whole list (V72 is the last full statement).
ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_type
    CHECK (movement_type IN ('RECEIVE','ISSUE','ADJUST_IN','ADJUST_OUT','REVERSAL','LOT_STATUS_CHANGE',
                             'TRANSFER_OUT','TRANSFER_IN','LOT_SPLIT_OUT','LOT_SPLIT_IN'));

CREATE TABLE purchase_quality_decisions (
    decision_id            UUID PRIMARY KEY,
    goods_receipt_id       UUID           NOT NULL REFERENCES goods_receipts (goods_receipt_id),
    goods_receipt_line_id  UUID           NOT NULL REFERENCES goods_receipt_lines (goods_receipt_line_id),
    accepted_quantity      NUMERIC(19, 6) NOT NULL,
    rejected_quantity      NUMERIC(19, 6) NOT NULL,
    reason                 VARCHAR(500),
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
    CONSTRAINT chk_purchase_qc_quantities
        CHECK (accepted_quantity >= 0 AND rejected_quantity >= 0 AND accepted_quantity + rejected_quantity > 0),
    CONSTRAINT chk_purchase_qc_reject_reason
        CHECK (rejected_quantity = 0 OR (reason IS NOT NULL AND btrim(reason) <> ''))
);

CREATE INDEX idx_purchase_qc_line_decided_at ON purchase_quality_decisions (goods_receipt_line_id, decided_at);
CREATE INDEX idx_purchase_qc_receipt_decided_at ON purchase_quality_decisions (goods_receipt_id, decided_at);

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_PURCHASE_QC_READ', 'PURCHASE_QC', 'READ', 'Read the purchase QC queue and QC decisions'),
    ('PERM_PURCHASE_QC_MANAGE', 'PURCHASE_QC', 'MANAGE', 'Record purchase QC decisions on received goods')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_PURCHASE_QC_READ', 'PERM_PURCHASE_QC_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
