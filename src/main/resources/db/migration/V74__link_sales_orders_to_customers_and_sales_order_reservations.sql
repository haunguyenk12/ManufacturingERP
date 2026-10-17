-- V74__link_sales_orders_to_customers_and_sales_order_reservations.sql
-- G6a (FE_HANDOFF_GD_06_2026-10-14.md): customer-linked sales orders, server-generated order numbers,
-- cancel reason, and stock held for a sales order line.
--
-- Purely additive for existing rows. Nothing is backfilled:
--   * customer_id stays NULL on every order created before G6 — matching the free-text customer_name
--     against the customer master would be a guess (G2 §9.5), and NULL is the honest answer
--     ("created before customers were linked").
--   * every existing reservation keeps its work_order_id / component_line_id, so the new CHECK holds
--     for all historical rows without touching them.

ALTER TABLE sales_orders
    ADD COLUMN customer_id        UUID         REFERENCES customers (customer_id),
    ADD COLUMN customer_code      VARCHAR(100),
    ADD COLUMN customer_reference VARCHAR(255),
    ADD COLUMN cancel_reason      TEXT;

CREATE INDEX idx_sales_orders_customer_id ON sales_orders (customer_id);

-- Server-generated order numbers (POST /sales-orders/v1/drafts). A sequence rather than
-- "count + 1": two concurrent drafts must never be handed the same number. The service still checks
-- uniqueness per company and skips a value an older, client-chosen orderNo already took.
CREATE SEQUENCE sales_order_number_seq START WITH 1 INCREMENT BY 1;

-- Stock held for a sales order line. Same table as work-order reservations on purpose
-- (NEXT_PHASE_PLAN.md §13.2 D4): the held quantity already lives in ONE place —
-- stock_balances.reserved_quantity — and every availability figure (MRP, dashboard, issue gates,
-- counts, transfers) reads that column. A second reservation table would not change that number,
-- but it would give "who holds this stock" two answers.
ALTER TABLE material_reservations
    ALTER COLUMN work_order_id DROP NOT NULL,
    ALTER COLUMN component_line_id DROP NOT NULL,
    ADD COLUMN sales_order_id      UUID REFERENCES sales_orders (sales_order_id),
    ADD COLUMN sales_order_line_id UUID REFERENCES sales_order_lines (sales_order_line_id);

-- Exactly one owner: a work order component line, or a sales order line — never both, never neither.
ALTER TABLE material_reservations
    ADD CONSTRAINT chk_material_reservations_single_owner CHECK (
        (work_order_id IS NOT NULL AND component_line_id IS NOT NULL
            AND sales_order_id IS NULL AND sales_order_line_id IS NULL)
        OR
        (work_order_id IS NULL AND component_line_id IS NULL
            AND sales_order_id IS NOT NULL AND sales_order_line_id IS NOT NULL)
    );

CREATE INDEX idx_material_reservations_sales_order_id ON material_reservations (sales_order_id);
CREATE INDEX idx_material_reservations_sales_order_line_id ON material_reservations (sales_order_line_id);
