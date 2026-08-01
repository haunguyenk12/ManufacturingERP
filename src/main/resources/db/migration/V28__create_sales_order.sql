-- V28__create_sales_order.sql
-- Phase F3: Sales Order header + lines. This is the independent demand source that feeds
-- planning_demands (demand_type = 'SALES_ORDER'); planning_demands itself needs no change,
-- its demand_type CHECK already allows the value since V18.

CREATE TABLE sales_orders (
    sales_order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id     UUID         NOT NULL REFERENCES companies(company_id),
    plant_id       UUID         NOT NULL REFERENCES plants(plant_id),
    order_no       VARCHAR(100) NOT NULL,
    customer_name  VARCHAR(255) NOT NULL,
    order_date     DATE         NOT NULL,
    status         VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    note           TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT       DEFAULT 0,
    CONSTRAINT uk_sales_orders_company_order_no UNIQUE (company_id, order_no),
    CONSTRAINT chk_sales_orders_status CHECK (status IN (
        'DRAFT', 'CONFIRMED', 'IN_PRODUCTION', 'PARTIALLY_FULFILLED', 'FULFILLED', 'CANCELLED'
    ))
);

CREATE TABLE sales_order_lines (
    sales_order_line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id      UUID          NOT NULL REFERENCES sales_orders(sales_order_id),
    item_id             UUID          NOT NULL REFERENCES items(item_id),
    line_no             INTEGER       NOT NULL,
    ordered_quantity    NUMERIC(19,6) NOT NULL,
    fulfilled_quantity  NUMERIC(19,6) NOT NULL DEFAULT 0,
    due_date            DATE          NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT        DEFAULT 0,
    CONSTRAINT uk_sales_order_lines_order_line_no UNIQUE (sales_order_id, line_no),
    CONSTRAINT chk_sales_order_lines_line_no CHECK (line_no > 0),
    CONSTRAINT chk_sales_order_lines_ordered CHECK (ordered_quantity > 0),
    -- fulfilled_quantity stays 0 for the whole of F3; the ceiling is declared here so F6 cannot
    -- over-fulfil a line even if application code regresses (spec §7.1).
    CONSTRAINT chk_sales_order_lines_fulfilled CHECK (
        fulfilled_quantity >= 0 AND fulfilled_quantity <= ordered_quantity
    )
);

CREATE INDEX idx_sales_orders_company_plant_status ON sales_orders(company_id, plant_id, status);

-- Serves the planning-demand query: filter by plant + status on the header, then due_date on the line.
CREATE INDEX idx_sales_order_lines_order_id ON sales_order_lines(sales_order_id);
CREATE INDEX idx_sales_order_lines_item_id ON sales_order_lines(item_id);
CREATE INDEX idx_sales_order_lines_due_date ON sales_order_lines(due_date);
