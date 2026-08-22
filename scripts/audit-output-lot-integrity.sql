-- Read-only, idempotent pre-deploy audit for ISS-08. Any returned row blocks deployment.

-- Approved receipts whose lot-tracked output was never linked to a lot/movement.
SELECT r.receipt_id, r.code, r.status, l.receipt_line_id, l.item_id, l.requested_lot_code
FROM production_receipts r
JOIN production_receipt_lines l ON l.receipt_id = r.receipt_id
JOIN items i ON i.item_id = l.item_id
WHERE r.status = 'APPROVED'
  AND i.lot_tracked = TRUE
  AND (l.lot_id IS NULL OR l.stock_movement_id IS NULL);

-- Conflicts under the published trim-only, case-sensitive identity policy.
SELECT item_id, btrim(lot_code) AS normalized_lot_code, count(*) AS conflicting_rows,
       array_agg(lot_id ORDER BY lot_id) AS lot_ids
FROM inventory_lots
GROUP BY item_id, btrim(lot_code)
HAVING count(*) > 1;

-- Approved output lots that are not in a QC-dispositionable or terminal state.
SELECT r.receipt_id, r.code, l.lot_id, il.lot_code, il.status
FROM production_receipts r
JOIN production_receipt_lines l ON l.receipt_id = r.receipt_id
JOIN inventory_lots il ON il.lot_id = l.lot_id
WHERE r.status = 'APPROVED'
  AND il.status NOT IN ('HOLD', 'AVAILABLE', 'REJECTED');
