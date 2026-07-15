-- V22: Add cancellation fields to goods_receipts for Cancel Goods Receipt feature

ALTER TABLE goods_receipts
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancel_note  TEXT;

-- Check constraint: if status = CANCELLED then cancelled_at must be set
ALTER TABLE goods_receipts
    ADD CONSTRAINT chk_goods_receipt_cancelled_at
    CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL);
