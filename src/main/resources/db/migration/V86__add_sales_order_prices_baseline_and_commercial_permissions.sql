-- G10a (FE handoff GĐ10): the sales order side of deliveries, invoices and payments.
--
--   * sales_order_lines.unit_price: NULL = no price yet, 0 = known to be zero (FE R02). VND only, so there is no
--     currency column; up to 999 999 999 with 6 decimals (FE §3.2).
--   * sales_orders.pricing_revision: bumped by every change to a line price; the API exposes it as an opaque
--     pricingVersion that invoices check against (G10c).
--   * sales_orders.delivery_baseline_known (D3): FALSE for every order already past DRAFT — nothing records what left
--     the warehouse for it before G10, and 0 would be a guess (same rule as B77). DRAFT orders have shipped nothing,
--     so they are TRUE, as is every order saved from now on. Not backfilled any other way.
--   * sales_order_price_events: one row per price set on a confirmed order (POST …/commercial-prices). Never updated
--     or deleted. Prices of a DRAFT change with the draft itself and are covered by the order's audit trail.
--   * commercial_idempotency_records: Idempotency-Key of every GĐ10 command, scoped by command, never expires (same
--     shape as work_order_idempotency_records, V79).
--   * Ten permissions for ADMIN and MANAGER.

ALTER TABLE sales_order_lines
    ADD COLUMN unit_price NUMERIC(19, 6);

ALTER TABLE sales_order_lines
    ADD CONSTRAINT chk_sales_order_lines_unit_price
        CHECK (unit_price IS NULL OR (unit_price >= 0 AND unit_price <= 999999999));

ALTER TABLE sales_orders
    ADD COLUMN pricing_revision        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN delivery_baseline_known BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE sales_orders SET delivery_baseline_known = FALSE WHERE status <> 'DRAFT';

ALTER TABLE sales_orders
    ADD CONSTRAINT chk_sales_orders_pricing_revision CHECK (pricing_revision >= 0);

CREATE TABLE sales_order_price_events (
    event_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id      UUID           NOT NULL REFERENCES sales_orders (sales_order_id),
    sales_order_line_id UUID           NOT NULL REFERENCES sales_order_lines (sales_order_line_id),
    previous_unit_price NUMERIC(19, 6),
    unit_price          NUMERIC(19, 6) NOT NULL,
    pricing_revision    INTEGER        NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT         DEFAULT 0,
    CONSTRAINT chk_sales_order_price_events_unit_price CHECK (unit_price >= 0)
);

CREATE INDEX idx_sales_order_price_events_order_id ON sales_order_price_events (sales_order_id);
CREATE INDEX idx_sales_order_price_events_line_id ON sales_order_price_events (sales_order_line_id);

CREATE TABLE commercial_idempotency_records (
    record_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(120) NOT NULL,
    command         VARCHAR(40)  NOT NULL,
    resource_id     UUID         NOT NULL,
    payload_hash    VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       DEFAULT 0,
    CONSTRAINT uk_commercial_idempotency_key_command UNIQUE (idempotency_key, command)
);

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_DELIVERY_READ', 'DELIVERY', 'READ', 'Read deliveries and their stock sources'),
    ('PERM_DELIVERY_MANAGE', 'DELIVERY', 'MANAGE', 'Create, edit and cancel draft deliveries'),
    ('PERM_DELIVERY_POST', 'DELIVERY', 'POST', 'Post deliveries (issue stock to the customer)'),
    ('PERM_SALES_INVOICE_READ', 'SALES_INVOICE', 'READ', 'Read sales invoices and their balances'),
    ('PERM_SALES_INVOICE_MANAGE', 'SALES_INVOICE', 'MANAGE', 'Create, edit and cancel draft sales invoices'),
    ('PERM_SALES_INVOICE_POST', 'SALES_INVOICE', 'POST', 'Post sales invoices'),
    ('PERM_CUSTOMER_PAYMENT_READ', 'CUSTOMER_PAYMENT', 'READ', 'Read customer payments and their allocations'),
    ('PERM_CUSTOMER_PAYMENT_MANAGE', 'CUSTOMER_PAYMENT', 'MANAGE', 'Create, edit and cancel draft customer payments'),
    ('PERM_CUSTOMER_PAYMENT_POST', 'CUSTOMER_PAYMENT', 'POST', 'Post customer payments'),
    ('PERM_SALES_PRICE_MANAGE', 'SALES_PRICE', 'MANAGE', 'See and set sales order line prices')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN (
    'PERM_DELIVERY_READ', 'PERM_DELIVERY_MANAGE', 'PERM_DELIVERY_POST',
    'PERM_SALES_INVOICE_READ', 'PERM_SALES_INVOICE_MANAGE', 'PERM_SALES_INVOICE_POST',
    'PERM_CUSTOMER_PAYMENT_READ', 'PERM_CUSTOMER_PAYMENT_MANAGE', 'PERM_CUSTOMER_PAYMENT_POST',
    'PERM_SALES_PRICE_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
