-- V76__extend_purchase_order_lifecycle.sql
-- G7a (FE_HANDOFF_GD_07_2026-10-14.md, NEXT_PHASE_PLAN.md §14): purchase order lifecycle.
--
--   * status SENT -> CONFIRMED, RECEIVED -> FULLY_RECEIVED, new CLOSED (D1; precedent V26).
--   * closed_quantity per line: the remainder a close gave up on. open = ordered - received - closed.
--   * reference, cancel/close reason and who/when of confirm/cancel/close.
--   * purchase_order_number_seq for server-numbered drafts (D2, mirror of sales_order_number_seq).
--   * purchasing_idempotency_records: Idempotency-Key for purchase order commands.
--   * released_* on supply_suggestion_conversions: cancelling a purchase order gives its planning
--     quota back once (FE Q12); the row is kept for trace.
--
-- Data: the two renamed statuses are updated in place; nothing else is backfilled. A NULL
-- confirmed_at on an order that is CONFIRMED means "sent before G7a", not "never confirmed".

-- The old CHECK must go first: it does not accept the new names, so renaming under it fails on
-- every database that has a SENT or RECEIVED order (an empty one would not notice).
ALTER TABLE purchase_orders DROP CONSTRAINT chk_purchase_orders_status;

UPDATE purchase_orders SET status = 'CONFIRMED' WHERE status = 'SENT';
UPDATE purchase_orders SET status = 'FULLY_RECEIVED' WHERE status = 'RECEIVED';

ALTER TABLE purchase_orders
    ADD CONSTRAINT chk_purchase_orders_status CHECK (status IN (
        'DRAFT', 'CONFIRMED', 'PARTIALLY_RECEIVED', 'FULLY_RECEIVED', 'CLOSED', 'CANCELLED'));

ALTER TABLE purchase_orders
    ADD COLUMN reference     VARCHAR(200),
    ADD COLUMN status_reason VARCHAR(500),
    -- Status right before the order was cancelled or closed; history reads it instead of guessing.
    ADD COLUMN previous_status VARCHAR(30),
    ADD COLUMN confirmed_at  TIMESTAMPTZ,
    ADD COLUMN confirmed_by  UUID,
    ADD COLUMN cancelled_at  TIMESTAMPTZ,
    ADD COLUMN cancelled_by  UUID,
    ADD COLUMN closed_at     TIMESTAMPTZ,
    ADD COLUMN closed_by     UUID;

CREATE INDEX idx_purchase_orders_plant_order_date ON purchase_orders (plant_id, order_date);

ALTER TABLE purchase_order_lines
    ADD COLUMN closed_quantity NUMERIC(19,6) NOT NULL DEFAULT 0;

ALTER TABLE purchase_order_lines DROP CONSTRAINT chk_po_lines_received_quantity;
ALTER TABLE purchase_order_lines
    ADD CONSTRAINT chk_po_lines_received_quantity CHECK (received_quantity >= 0),
    ADD CONSTRAINT chk_po_lines_closed_quantity CHECK (closed_quantity >= 0),
    ADD CONSTRAINT chk_po_lines_received_closed_within_ordered
        CHECK (received_quantity + closed_quantity <= ordered_quantity);

CREATE SEQUENCE purchase_order_number_seq START WITH 1 INCREMENT BY 1;

-- One row per (key, command). A replay with the same key finds the row and returns the resource's
-- current state; a different payload under the same key is IDEMPOTENCY_CONFLICT. No TTL.
CREATE TABLE purchasing_idempotency_records (
    record_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(120) NOT NULL,
    command          VARCHAR(40)  NOT NULL,
    resource_id      UUID         NOT NULL,
    payload_hash     VARCHAR(64)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       DEFAULT 0,
    CONSTRAINT uk_purchasing_idempotency_key_command UNIQUE (idempotency_key, command)
);

CREATE INDEX idx_purchasing_idempotency_resource ON purchasing_idempotency_records (resource_id);

ALTER TABLE supply_suggestion_conversions
    ADD COLUMN released_at    TIMESTAMPTZ,
    ADD COLUMN released_by    UUID,
    ADD COLUMN release_reason VARCHAR(500);

CREATE INDEX idx_supply_suggestion_conversions_target ON supply_suggestion_conversions (target_type, target_id);
