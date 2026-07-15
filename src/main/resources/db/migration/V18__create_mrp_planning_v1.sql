-- V18__create_mrp_planning_v1.sql
-- MRP & Procurement Planning v1: planning demand, run history, requirements, and supply suggestions.

CREATE TABLE planning_demands (
    planning_demand_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id         UUID          NOT NULL REFERENCES companies(company_id),
    plant_id           UUID          NOT NULL REFERENCES plants(plant_id),
    item_id            UUID          NOT NULL REFERENCES items(item_id),
    warehouse_id       UUID          REFERENCES warehouses(warehouse_id),
    demand_type        VARCHAR(30)   NOT NULL,
    required_quantity  NUMERIC(19,6) NOT NULL,
    due_date           DATE          NOT NULL,
    priority           INTEGER       NOT NULL DEFAULT 100,
    status             VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    reference_type     VARCHAR(80),
    reference_id       VARCHAR(120),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT        DEFAULT 0,
    CONSTRAINT chk_planning_demands_type CHECK (demand_type IN ('MANUAL', 'FORECAST', 'SALES_ORDER')),
    CONSTRAINT chk_planning_demands_quantity CHECK (required_quantity > 0),
    CONSTRAINT chk_planning_demands_priority CHECK (priority >= 0),
    CONSTRAINT chk_planning_demands_status CHECK (status IN ('OPEN', 'CANCELLED', 'CONSUMED'))
);

CREATE TABLE mrp_runs (
    mrp_run_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id              UUID         NOT NULL REFERENCES companies(company_id),
    plant_id                UUID         NOT NULL REFERENCES plants(plant_id),
    warehouse_id            UUID         REFERENCES warehouses(warehouse_id),
    horizon_start_date      DATE         NOT NULL,
    horizon_end_date        DATE         NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    total_demand_lines      INTEGER      NOT NULL DEFAULT 0,
    total_requirement_lines INTEGER      NOT NULL DEFAULT 0,
    total_suggestion_lines  INTEGER      NOT NULL DEFAULT 0,
    error_message           TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT       DEFAULT 0,
    CONSTRAINT chk_mrp_runs_horizon CHECK (horizon_start_date <= horizon_end_date),
    CONSTRAINT chk_mrp_runs_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_mrp_runs_totals CHECK (
        total_demand_lines >= 0
        AND total_requirement_lines >= 0
        AND total_suggestion_lines >= 0
    )
);

CREATE TABLE mrp_run_demands (
    mrp_run_demand_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mrp_run_id         UUID          NOT NULL REFERENCES mrp_runs(mrp_run_id),
    planning_demand_id UUID          NOT NULL REFERENCES planning_demands(planning_demand_id),
    item_id            UUID          NOT NULL REFERENCES items(item_id),
    warehouse_id       UUID          REFERENCES warehouses(warehouse_id),
    required_quantity  NUMERIC(19,6) NOT NULL,
    due_date           DATE          NOT NULL,
    priority           INTEGER       NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'SNAPSHOT',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT        DEFAULT 0,
    CONSTRAINT uk_mrp_run_demands_run_demand UNIQUE (mrp_run_id, planning_demand_id),
    CONSTRAINT chk_mrp_run_demands_quantity CHECK (required_quantity > 0),
    CONSTRAINT chk_mrp_run_demands_status CHECK (status IN ('SNAPSHOT'))
);

CREATE TABLE mrp_requirement_lines (
    mrp_requirement_line_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mrp_run_id                  UUID          NOT NULL REFERENCES mrp_runs(mrp_run_id),
    parent_requirement_line_id  UUID          REFERENCES mrp_requirement_lines(mrp_requirement_line_id),
    source_demand_id            UUID          REFERENCES planning_demands(planning_demand_id),
    item_id                     UUID          NOT NULL REFERENCES items(item_id),
    warehouse_id                UUID          REFERENCES warehouses(warehouse_id),
    requirement_level           INTEGER       NOT NULL,
    gross_required_quantity     NUMERIC(19,6) NOT NULL DEFAULT 0,
    available_quantity          NUMERIC(19,6) NOT NULL DEFAULT 0,
    reserved_quantity           NUMERIC(19,6) NOT NULL DEFAULT 0,
    open_supply_quantity        NUMERIC(19,6) NOT NULL DEFAULT 0,
    safety_stock_quantity       NUMERIC(19,6) NOT NULL DEFAULT 0,
    net_required_quantity       NUMERIC(19,6) NOT NULL DEFAULT 0,
    due_date                    DATE          NOT NULL,
    requirement_status          VARCHAR(20)   NOT NULL,
    note                        TEXT,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID,
    version                     BIGINT        DEFAULT 0,
    CONSTRAINT chk_mrp_req_level CHECK (requirement_level >= 0),
    CONSTRAINT chk_mrp_req_quantities CHECK (
        gross_required_quantity >= 0
        AND available_quantity >= 0
        AND reserved_quantity >= 0
        AND open_supply_quantity >= 0
        AND safety_stock_quantity >= 0
        AND net_required_quantity >= 0
    ),
    CONSTRAINT chk_mrp_req_status CHECK (requirement_status IN ('COVERED', 'SHORTAGE', 'BOM_MISSING', 'INVALID'))
);

CREATE TABLE supply_suggestions (
    supply_suggestion_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mrp_run_id               UUID          NOT NULL REFERENCES mrp_runs(mrp_run_id),
    requirement_line_id      UUID          NOT NULL REFERENCES mrp_requirement_lines(mrp_requirement_line_id),
    company_id               UUID          NOT NULL REFERENCES companies(company_id),
    plant_id                 UUID          NOT NULL REFERENCES plants(plant_id),
    warehouse_id             UUID          REFERENCES warehouses(warehouse_id),
    item_id                  UUID          NOT NULL REFERENCES items(item_id),
    suggestion_type          VARCHAR(40)   NOT NULL,
    suggested_quantity       NUMERIC(19,6) NOT NULL,
    needed_by_date           DATE          NOT NULL,
    suggested_order_date     DATE          NOT NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    decision_note            TEXT,
    converted_reference_type VARCHAR(80),
    converted_reference_id   UUID,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    version                  BIGINT        DEFAULT 0,
    CONSTRAINT chk_supply_suggestions_type CHECK (suggestion_type IN ('WORK_ORDER', 'PURCHASE_REQUISITION')),
    CONSTRAINT chk_supply_suggestions_quantity CHECK (suggested_quantity > 0),
    CONSTRAINT chk_supply_suggestions_status CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'CONVERTED'))
);

CREATE INDEX idx_planning_demands_company_plant_status_due
    ON planning_demands(company_id, plant_id, status, due_date);
CREATE INDEX idx_planning_demands_item_id ON planning_demands(item_id);
CREATE INDEX idx_planning_demands_warehouse_id ON planning_demands(warehouse_id);

CREATE INDEX idx_mrp_runs_company_plant_status_created
    ON mrp_runs(company_id, plant_id, status, created_at DESC);
CREATE INDEX idx_mrp_runs_warehouse_id ON mrp_runs(warehouse_id);

CREATE INDEX idx_mrp_run_demands_run_id ON mrp_run_demands(mrp_run_id);
CREATE INDEX idx_mrp_run_demands_planning_demand_id ON mrp_run_demands(planning_demand_id);

CREATE INDEX idx_mrp_requirement_lines_run_item_status
    ON mrp_requirement_lines(mrp_run_id, item_id, requirement_status);
CREATE INDEX idx_mrp_requirement_lines_parent_id
    ON mrp_requirement_lines(parent_requirement_line_id);
CREATE INDEX idx_mrp_requirement_lines_due_date
    ON mrp_requirement_lines(due_date);

CREATE INDEX idx_supply_suggestions_run_status_type
    ON supply_suggestions(mrp_run_id, status, suggestion_type);
CREATE INDEX idx_supply_suggestions_item_status_needed
    ON supply_suggestions(item_id, status, needed_by_date);
CREATE INDEX idx_supply_suggestions_requirement_line_id
    ON supply_suggestions(requirement_line_id);
