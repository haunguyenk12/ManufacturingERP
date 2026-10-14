-- V69__create_customers_and_extend_suppliers.sql
-- G2-1: Customer master data (FE_HANDOFF_GD_02_2026-10-12.md §3.4), company-scoped like suppliers.
--
-- Deliberately NOT linked to sales_orders.customer_name: tying a sales order to a customer row is
-- outside phase G2 (NEXT_PHASE_PLAN.md §9.5). customer_name stays free text for now.
--
-- Uniqueness is (company_id, code) regardless of status, same as suppliers: a deactivated customer
-- still owns its code, so reactivating it can never collide with a newer row. A supplier and a
-- customer may share a code — they are different catalogues.

CREATE TABLE customers (
    customer_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   UUID         NOT NULL REFERENCES companies(company_id),
    code         VARCHAR(100) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    contact_name VARCHAR(255),
    email        VARCHAR(255),
    phone        VARCHAR(255),
    address      VARCHAR(500),
    notes        VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT       DEFAULT 0,
    CONSTRAINT uk_customers_company_code UNIQUE (company_id, code),
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_customers_company_status ON customers(company_id, status);

-- G2-2 aligns the Supplier DTOs with FE §3.3; the columns land here so both master catalogues
-- expose the same contact/notes shape. Nullable, no backfill: existing suppliers simply have none.
ALTER TABLE suppliers
    ADD COLUMN contact_name VARCHAR(255),
    ADD COLUMN notes        VARCHAR(500);
