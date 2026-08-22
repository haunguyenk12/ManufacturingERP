-- NON_TRACKED production output has no InventoryLot row to carry HOLD. Keep its on-hand quantity
-- visible while excluding it from reservation, issue and MRP until the Production Receipt receives
-- a QC AVAILABLE disposition.
ALTER TABLE stock_balances
    ADD COLUMN quality_hold_quantity NUMERIC(19,6) NOT NULL DEFAULT 0;

-- Repair existing approved receipts that have not received a QC disposition. The LEAST guard keeps
-- the migration valid if legacy free stock was already reserved or consumed before this fix.
WITH pending_quality AS (
    SELECT prl.item_id,
           prl.warehouse_id,
           SUM(prl.quantity) AS pending_quantity
    FROM production_receipt_lines prl
    JOIN production_receipts pr ON pr.receipt_id = prl.receipt_id
    WHERE pr.status = 'APPROVED'
      AND pr.qc_result IS NULL
      AND prl.stock_movement_id IS NOT NULL
      AND prl.lot_id IS NULL
      AND prl.serial_id IS NULL
    GROUP BY prl.item_id, prl.warehouse_id
)
UPDATE stock_balances b
SET quality_hold_quantity = LEAST(
        GREATEST(b.quantity - b.reserved_quantity, 0),
        pending_quality.pending_quantity)
FROM pending_quality
WHERE b.item_id = pending_quality.item_id
  AND b.warehouse_id = pending_quality.warehouse_id
  AND b.lot_id IS NULL;

ALTER TABLE stock_balances
    ADD CONSTRAINT chk_stock_balances_quality_hold_quantity
        CHECK (quality_hold_quantity >= 0
            AND reserved_quantity + quality_hold_quantity <= quantity);

CREATE INDEX idx_stock_balances_quality_hold_quantity
    ON stock_balances(quality_hold_quantity)
    WHERE quality_hold_quantity > 0;
