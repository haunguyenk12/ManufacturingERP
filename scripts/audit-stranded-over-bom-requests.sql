-- Read-only, idempotent pre-deploy audit for the DEC-09 Over-BOM approval gate.
-- Any returned row is a request that a manager can never approve: the approval posts stock through
-- InventoryMovementService, which refuses the line, so the document stays PENDING_APPROVAL forever
-- while the operator who knew the missing detail has moved on.
--
-- These rows can only be created by a build from BEFORE the request-time validation was added.
-- The fix stops new ones; it does not retro-resolve the ones already stored.
--
-- HOW TO CLEAR A ROW: reject it through the API —
--     POST /api/v1/work-orders/{workOrderId}/material-issues/{issueId}/reject   {"reason": "..."}
-- Rejection posts no stock, is audited, and leaves the document readable. Do NOT DELETE the row:
-- business documents are closed by status, never hard-deleted (coding-rules.md C6). The operator can
-- then submit a correct request naming the lot.

-- 1. Lot-tracked component, no lot named, and no reservation to inherit one from.
--    This is the shape that stranded MI-F193741D.
SELECT i.issue_id,
       i.code,
       i.status,
       i.requested_at,
       i.work_order_id,
       w.work_order_no,
       l.issue_line_id,
       it.code   AS item_code,
       l.quantity,
       l.reason_code,
       'LOT_REQUIRED: lot-tracked component with no requested lot and no reservation' AS diagnosis
FROM material_issues i
JOIN material_issue_lines l ON l.issue_id = i.issue_id
JOIN items it               ON it.item_id = l.item_id
JOIN work_orders w          ON w.work_order_id = i.work_order_id
WHERE i.status = 'PENDING_APPROVAL'
  AND it.lot_tracked = TRUE
  AND l.reservation_id IS NULL
  AND l.requested_lot_id IS NULL
  AND (l.requested_lot_code IS NULL OR btrim(l.requested_lot_code) = '');

-- 2. Serial-tracked material on a pending request. Serial Over-BOM is deferred (ISS-10), so the
--    approval refuses these too.
SELECT i.issue_id,
       i.code,
       i.status,
       i.requested_at,
       l.issue_line_id,
       it.code AS item_code,
       l.quantity,
       'SERIAL_DEFERRED: serial-tracked Over-BOM material is not supported' AS diagnosis
FROM material_issues i
JOIN material_issue_lines l ON l.issue_id = i.issue_id
JOIN items it               ON it.item_id = l.item_id
WHERE i.status = 'PENDING_APPROVAL'
  AND (it.serial_tracked = TRUE OR l.requested_serial_id IS NOT NULL);

-- 3. Pending request whose work order can no longer execute. Approval calls ensureExecutable, so a
--    CANCELLED/CLOSED/COMPLETED work order blocks it regardless of how well-formed the line is.
SELECT i.issue_id,
       i.code,
       i.status         AS issue_status,
       i.requested_at,
       w.work_order_no,
       w.status         AS work_order_status,
       'WORK_ORDER_STATE: approval requires RELEASED or IN_PROGRESS' AS diagnosis
FROM material_issues i
JOIN work_orders w ON w.work_order_id = i.work_order_id
WHERE i.status = 'PENDING_APPROVAL'
  AND w.status NOT IN ('RELEASED', 'IN_PROGRESS');

-- 4. Sanity counter — how many pending requests exist at all, so a clean audit can be told apart
--    from an audit that ran against the wrong database.
SELECT count(*) AS pending_approval_requests
FROM material_issues
WHERE status = 'PENDING_APPROVAL';
