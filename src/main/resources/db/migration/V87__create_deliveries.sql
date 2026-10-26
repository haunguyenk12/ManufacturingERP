-- G10b (FE handoff GĐ10): deliveries — warehouse dispatch of one sales order's goods to its customer.
--
--   * deliveries: one sales order, its master customer (snapshot of the name), DRAFT → POSTED | CANCELLED. A DRAFT
--     only claims quota on the order lines; stock moves at POST. POSTED and CANCELLED are final (no reversal, FE Q33).
--   * delivery_lines: one stock position (warehouse + lot, lot NULL for untracked items) per line. At POST the part
--     taken from the order's own holds is reserved_quantity, issued as one ISSUE (reserved_movement_id); the rest
--     comes from free stock as a second ISSUE (free_movement_id). Delivered and claimed quantities are always
--     derived from these rows — there is no counter column anywhere (same rule as B217).
--   * No backfill: nothing was ever delivered through the system before this migration.

CREATE SEQUENCE delivery_number_seq;

CREATE TABLE deliveries (
    delivery_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(40)  NOT NULL,
    company_id     UUID         NOT NULL REFERENCES companies (company_id),
    plant_id       UUID         NOT NULL REFERENCES plants (plant_id),
    sales_order_id UUID         NOT NULL REFERENCES sales_orders (sales_order_id),
    customer_id    UUID         NOT NULL REFERENCES customers (customer_id),
    customer_name  VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    document_date  DATE         NOT NULL,
    note           TEXT,
    posted_at      TIMESTAMPTZ,
    posted_by      UUID,
    cancelled_at   TIMESTAMPTZ,
    cancelled_by   UUID,
    cancel_reason  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT       DEFAULT 0,
    CONSTRAINT uk_deliveries_code UNIQUE (code),
    CONSTRAINT chk_deliveries_status CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    CONSTRAINT chk_deliveries_posted CHECK (status <> 'POSTED' OR posted_at IS NOT NULL),
    CONSTRAINT chk_deliveries_cancelled CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
);

CREATE INDEX idx_deliveries_plant_status ON deliveries (plant_id, status);
CREATE INDEX idx_deliveries_sales_order_id ON deliveries (sales_order_id);
CREATE INDEX idx_deliveries_customer_id ON deliveries (customer_id);
CREATE INDEX idx_deliveries_document_date ON deliveries (document_date);

CREATE TABLE delivery_lines (
    delivery_line_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id          UUID           NOT NULL REFERENCES deliveries (delivery_id),
    line_no              INTEGER        NOT NULL,
    sales_order_line_id  UUID           NOT NULL REFERENCES sales_order_lines (sales_order_line_id),
    item_id              UUID           NOT NULL REFERENCES items (item_id),
    warehouse_id         UUID           NOT NULL REFERENCES warehouses (warehouse_id),
    lot_id               UUID REFERENCES inventory_lots (lot_id),
    quantity             NUMERIC(19, 6) NOT NULL,
    reserved_quantity    NUMERIC(19, 6) NOT NULL DEFAULT 0,
    reserved_movement_id UUID REFERENCES stock_movements (movement_id),
    free_movement_id     UUID REFERENCES stock_movements (movement_id),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT         DEFAULT 0,
    CONSTRAINT uk_delivery_lines_delivery_line_no UNIQUE (delivery_id, line_no),
    CONSTRAINT chk_delivery_lines_quantity CHECK (quantity > 0),
    CONSTRAINT chk_delivery_lines_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity)
);

CREATE INDEX idx_delivery_lines_delivery_id ON delivery_lines (delivery_id);
CREATE INDEX idx_delivery_lines_sales_order_line_id ON delivery_lines (sales_order_line_id);
CREATE INDEX idx_delivery_lines_lot_id ON delivery_lines (lot_id);
