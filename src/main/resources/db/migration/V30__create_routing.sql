-- V30__create_routing.sql
-- Phase F4: minimal Routing master data + the immutable routing snapshot on work_orders.
-- Work Center is intentionally NOT modelled as an entity (spec §11 puts capacity/CRP outside the
-- MVP); routing_operations.work_center_code is a free-text reference.

CREATE TABLE routings (
    routing_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID         NOT NULL REFERENCES companies(company_id),
    item_id         UUID         NOT NULL REFERENCES items(item_id),
    code            VARCHAR(100) NOT NULL,
    -- Named routing_version, not version: `version` is the optimistic-locking column of BaseEntity.
    routing_version VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    note            TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       DEFAULT 0,
    CONSTRAINT uk_routings_company_code_version UNIQUE (company_id, code, routing_version),
    CONSTRAINT chk_routings_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

-- One ACTIVE routing per item (invariant B48) — the same rule BomService enforces for BOM (B7),
-- but declared in the schema so a concurrent activate cannot produce two ACTIVE revisions.
CREATE UNIQUE INDEX uk_routings_one_active_per_item
    ON routings(company_id, item_id)
    WHERE status = 'ACTIVE';

CREATE TABLE routing_operations (
    routing_operation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    routing_id           UUID          NOT NULL REFERENCES routings(routing_id),
    sequence             INTEGER       NOT NULL,
    name                 VARCHAR(255)  NOT NULL,
    work_center_code     VARCHAR(100)  NOT NULL,
    setup_minutes        NUMERIC(19,6) NOT NULL DEFAULT 0,
    run_minutes_per_unit NUMERIC(19,6) NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT        DEFAULT 0,
    CONSTRAINT uk_routing_operations_sequence UNIQUE (routing_id, sequence),
    CONSTRAINT chk_routing_operations_sequence CHECK (sequence > 0),
    CONSTRAINT chk_routing_operations_setup CHECK (setup_minutes >= 0),
    CONSTRAINT chk_routing_operations_run CHECK (run_minutes_per_unit >= 0)
);

CREATE INDEX idx_routings_company_id ON routings(company_id);
CREATE INDEX idx_routings_item_id ON routings(item_id);
CREATE INDEX idx_routings_status ON routings(status);
CREATE INDEX idx_routing_operations_routing_id ON routing_operations(routing_id);

-- Routing snapshot on the work order (spec §3.3 "Routing snapshot"). Copied values, not a live
-- read: source_routing_code/version stay frozen even if the routing master is revised afterwards.
-- Nullable because F4 only requires a routing on the MRP proposal path; manually created work
-- orders keep working without one (see NEXT_PHASE_PLAN F4 §1.3).
ALTER TABLE work_orders
    ADD COLUMN source_routing_id      UUID REFERENCES routings(routing_id),
    ADD COLUMN source_routing_code    VARCHAR(100),
    ADD COLUMN source_routing_version VARCHAR(40),
    ADD COLUMN routing_captured_at    TIMESTAMPTZ;

CREATE INDEX idx_work_orders_source_routing_id ON work_orders(source_routing_id);
