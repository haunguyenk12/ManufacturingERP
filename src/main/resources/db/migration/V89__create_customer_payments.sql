-- G10d (FE handoff GĐ10): customer payments — a record of money received from one customer, allocated to POSTED sales
-- invoices of one plant. No bank transfer, no stock effect.
--
--   * customer_payments: DRAFT → POSTED | CANCELLED, VND whole dong (NUMERIC(19,0)), amount > 0 and equal to the sum of
--     its allocations (checked by the service: a table CHECK cannot see the child rows).
--   * customer_payment_allocations: one invoice each, amount > 0. Paid / outstanding of an invoice are always summed
--     from the allocations of POSTED payments; DRAFT allocations only claim (no counter column).

CREATE SEQUENCE customer_payment_number_seq;

CREATE TABLE customer_payments (
    payment_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(40)    NOT NULL,
    company_id    UUID           NOT NULL REFERENCES companies (company_id),
    plant_id      UUID           NOT NULL REFERENCES plants (plant_id),
    customer_id   UUID           NOT NULL REFERENCES customers (customer_id),
    customer_name VARCHAR(255)   NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    method        VARCHAR(20)    NOT NULL,
    reference     VARCHAR(200),
    document_date DATE           NOT NULL,
    note          TEXT,
    amount        NUMERIC(19, 0) NOT NULL,
    posted_at     TIMESTAMPTZ,
    posted_by     UUID,
    cancelled_at  TIMESTAMPTZ,
    cancelled_by  UUID,
    cancel_reason TEXT,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT         DEFAULT 0,
    CONSTRAINT uk_customer_payments_code UNIQUE (code),
    CONSTRAINT chk_customer_payments_status CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    CONSTRAINT chk_customer_payments_method CHECK (method IN ('CASH', 'TRANSFER', 'OTHER')),
    CONSTRAINT chk_customer_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_customer_payments_posted CHECK (status <> 'POSTED' OR posted_at IS NOT NULL),
    CONSTRAINT chk_customer_payments_cancelled CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
);

CREATE INDEX idx_customer_payments_plant_status ON customer_payments (plant_id, status);
CREATE INDEX idx_customer_payments_customer_id ON customer_payments (customer_id);
CREATE INDEX idx_customer_payments_document_date ON customer_payments (document_date);

CREATE TABLE customer_payment_allocations (
    allocation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id    UUID           NOT NULL REFERENCES customer_payments (payment_id),
    line_no       INTEGER        NOT NULL,
    invoice_id    UUID           NOT NULL REFERENCES sales_invoices (invoice_id),
    amount        NUMERIC(19, 0) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT         DEFAULT 0,
    CONSTRAINT uk_customer_payment_allocations_payment_line_no UNIQUE (payment_id, line_no),
    CONSTRAINT uk_customer_payment_allocations_payment_invoice UNIQUE (payment_id, invoice_id),
    CONSTRAINT chk_customer_payment_allocations_amount CHECK (amount > 0)
);

CREATE INDEX idx_customer_payment_allocations_payment_id ON customer_payment_allocations (payment_id);
CREATE INDEX idx_customer_payment_allocations_invoice_id ON customer_payment_allocations (invoice_id);
