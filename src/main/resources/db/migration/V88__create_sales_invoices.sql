-- G10c (FE handoff GĐ10): sales invoices — internal billing of POSTED delivery lines of one sales order.
--
--   * sales_invoices: one sales order and its master customer, DRAFT → POSTED | CANCELLED, VND only. Amounts are
--     whole dong (NUMERIC(19,0)); the header amount is the sum of its lines. No stock effect, ever.
--   * sales_invoice_lines: one POSTED delivery line each, with the locked order-line price and the cumulative billing
--     base it was priced on (billed_quantity_before / billed_amount_before of the order line from POSTED invoices).
--     amount = ROUND_HALF_UP((before_qty + qty) × price, 0) − before_amount, in line order (FE R14) — so three partial
--     invoices of 1/3 at 1 dong bill 0, 1, 0 and never drift. Claims and billed quantities are always derived from
--     these rows (no counter column).

CREATE SEQUENCE sales_invoice_number_seq;

CREATE TABLE sales_invoices (
    invoice_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(40)    NOT NULL,
    company_id       UUID           NOT NULL REFERENCES companies (company_id),
    plant_id         UUID           NOT NULL REFERENCES plants (plant_id),
    sales_order_id   UUID           NOT NULL REFERENCES sales_orders (sales_order_id),
    customer_id      UUID           NOT NULL REFERENCES customers (customer_id),
    customer_name    VARCHAR(255)   NOT NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    document_date    DATE           NOT NULL,
    due_date         DATE           NOT NULL,
    note             TEXT,
    amount           NUMERIC(19, 0) NOT NULL,
    billing_snapshot VARCHAR(80)    NOT NULL,
    posted_at        TIMESTAMPTZ,
    posted_by        UUID,
    cancelled_at     TIMESTAMPTZ,
    cancelled_by     UUID,
    cancel_reason    TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT         DEFAULT 0,
    CONSTRAINT uk_sales_invoices_code UNIQUE (code),
    CONSTRAINT chk_sales_invoices_status CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    CONSTRAINT chk_sales_invoices_amount CHECK (amount >= 0),
    CONSTRAINT chk_sales_invoices_due_date CHECK (due_date >= document_date),
    CONSTRAINT chk_sales_invoices_posted CHECK (status <> 'POSTED' OR posted_at IS NOT NULL),
    CONSTRAINT chk_sales_invoices_cancelled CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
);

CREATE INDEX idx_sales_invoices_plant_status ON sales_invoices (plant_id, status);
CREATE INDEX idx_sales_invoices_sales_order_id ON sales_invoices (sales_order_id);
CREATE INDEX idx_sales_invoices_customer_id ON sales_invoices (customer_id);
CREATE INDEX idx_sales_invoices_document_date ON sales_invoices (document_date);

CREATE TABLE sales_invoice_lines (
    invoice_line_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id             UUID           NOT NULL REFERENCES sales_invoices (invoice_id),
    line_no                INTEGER        NOT NULL,
    delivery_line_id       UUID           NOT NULL REFERENCES delivery_lines (delivery_line_id),
    sales_order_line_id    UUID           NOT NULL REFERENCES sales_order_lines (sales_order_line_id),
    quantity               NUMERIC(19, 6) NOT NULL,
    unit_price             NUMERIC(19, 6) NOT NULL,
    amount                 NUMERIC(19, 0) NOT NULL,
    billed_quantity_before NUMERIC(19, 6) NOT NULL,
    billed_amount_before   NUMERIC(19, 0) NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    version                BIGINT         DEFAULT 0,
    CONSTRAINT uk_sales_invoice_lines_invoice_line_no UNIQUE (invoice_id, line_no),
    CONSTRAINT uk_sales_invoice_lines_invoice_delivery_line UNIQUE (invoice_id, delivery_line_id),
    CONSTRAINT chk_sales_invoice_lines_quantity CHECK (quantity > 0),
    CONSTRAINT chk_sales_invoice_lines_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_sales_invoice_lines_amount CHECK (amount >= 0)
);

CREATE INDEX idx_sales_invoice_lines_invoice_id ON sales_invoice_lines (invoice_id);
CREATE INDEX idx_sales_invoice_lines_delivery_line_id ON sales_invoice_lines (delivery_line_id);
CREATE INDEX idx_sales_invoice_lines_sales_order_line_id ON sales_invoice_lines (sales_order_line_id);
