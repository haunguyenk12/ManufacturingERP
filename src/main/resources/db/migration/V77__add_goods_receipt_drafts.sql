-- V77__add_goods_receipt_drafts.sql
-- G7b (FE_HANDOFF_GD_07_2026-10-14.md, NEXT_PHASE_PLAN.md §14): goods receipt drafts.
--
--   * status DRAFT: saved, edited, cancelled without touching stock; POST moves it to POSTED (D3).
--     POSTED receipts keep the legacy cancel-with-REVERSAL path.
--   * posted_at / idempotency_key become nullable: a draft has neither. A POSTED receipt still
--     always has posted_at (CHECK).
--   * received_date (business date, plant time zone) — backfilled from posted_at in the plant's
--     zone: that is the same fact the row already recorded, not a guess.
--   * goods_receipt_lines.stock_movement_id nullable (a draft line has no movement yet), expires_on.
--   * goods_receipt_number_seq for server-numbered drafts.
--   * PERM_GOODS_RECEIPT_READ / _MANAGE (D9, FE Q24): ADMIN and MANAGER only.

ALTER TABLE goods_receipts DROP CONSTRAINT chk_goods_receipts_status;
ALTER TABLE goods_receipts
    ADD CONSTRAINT chk_goods_receipts_status CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED'));

ALTER TABLE goods_receipts ALTER COLUMN posted_at DROP DEFAULT;
ALTER TABLE goods_receipts ALTER COLUMN posted_at DROP NOT NULL;
ALTER TABLE goods_receipts ALTER COLUMN idempotency_key DROP NOT NULL;

ALTER TABLE goods_receipts
    ADD COLUMN received_date DATE,
    ADD COLUMN reference     VARCHAR(200),
    ADD COLUMN posted_by     UUID,
    ADD COLUMN cancelled_by  UUID,
    -- TRUE once posted on hold (G7b, D4). Receipts posted before went straight to free stock, so
    -- reversing one of them must not release a quality hold it never created.
    ADD COLUMN quality_held  BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE goods_receipts gr
SET received_date = (gr.posted_at AT TIME ZONE p.timezone)::date
FROM purchase_orders po
JOIN plants p ON p.plant_id = po.plant_id
WHERE po.purchase_order_id = gr.purchase_order_id;

ALTER TABLE goods_receipts ALTER COLUMN received_date SET NOT NULL;

ALTER TABLE goods_receipts
    ADD CONSTRAINT chk_goods_receipts_posted_at CHECK (status <> 'POSTED' OR posted_at IS NOT NULL);

CREATE INDEX idx_goods_receipts_warehouse_received_date ON goods_receipts (warehouse_id, received_date);

ALTER TABLE goods_receipt_lines ALTER COLUMN stock_movement_id DROP NOT NULL;
ALTER TABLE goods_receipt_lines ADD COLUMN expires_on DATE;

CREATE SEQUENCE goods_receipt_number_seq START WITH 1 INCREMENT BY 1;

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_GOODS_RECEIPT_READ', 'GOODS_RECEIPT', 'READ', 'Read goods receipts and the purchase orders open for receiving'),
    ('PERM_GOODS_RECEIPT_MANAGE', 'GOODS_RECEIPT', 'MANAGE', 'Create, edit and cancel draft goods receipts')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_GOODS_RECEIPT_READ', 'PERM_GOODS_RECEIPT_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
