-- G11a (FE handoff GĐ11): hourly cost rates of work centers and the cost baseline captured when a work order is
-- released. No stock effect, no value on stock rows (D1, D3): the material price of a baseline is the item's standard
-- cost (P3) at the second of RELEASE, never a warehouse average — the warehouse knows quantities only.
--
--   * production_cost_rates: one immutable row per saved rate; the current rate of a work center is its highest
--     rate_version (no row = never priced, version 0). History is the rows themselves.
--   * production_cost_baselines (+ materials, operations): captured once, inside the release transaction. A missing
--     standard cost or rate stays NULL ("unknown", D4) — it never blocks release and is never back-filled later with
--     today's price (B77). Work orders released before G11 simply have no baseline.

CREATE TABLE production_cost_rates (
    rate_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_center_id UUID           NOT NULL REFERENCES work_centers (work_center_id),
    plant_id       UUID           NOT NULL REFERENCES plants (plant_id),
    rate_version   INTEGER        NOT NULL,
    labor_rate     NUMERIC(19, 6) NOT NULL,
    overhead_rate  NUMERIC(19, 6) NOT NULL,
    currency       VARCHAR(3)     NOT NULL DEFAULT 'VND',
    reason         TEXT           NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT         DEFAULT 0,
    CONSTRAINT uk_production_cost_rates_center_version UNIQUE (work_center_id, rate_version),
    CONSTRAINT chk_production_cost_rates_version CHECK (rate_version > 0),
    CONSTRAINT chk_production_cost_rates_labor CHECK (labor_rate >= 0 AND labor_rate <= 999999999),
    CONSTRAINT chk_production_cost_rates_overhead CHECK (overhead_rate >= 0 AND overhead_rate <= 999999999),
    CONSTRAINT chk_production_cost_rates_currency CHECK (currency = 'VND')
);

CREATE INDEX idx_production_cost_rates_plant_id ON production_cost_rates (plant_id);

CREATE TABLE production_cost_baselines (
    baseline_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id           UUID           NOT NULL REFERENCES work_orders (work_order_id),
    plant_id                UUID           NOT NULL REFERENCES plants (plant_id),
    output_item_id          UUID           NOT NULL REFERENCES items (item_id),
    output_warehouse_id     UUID           NOT NULL REFERENCES warehouses (warehouse_id),
    -- On hand of the output item in the output warehouse at release: the isolated output pool (D3) starts from 0.
    output_opening_quantity NUMERIC(19, 6) NOT NULL,
    planned_quantity        NUMERIC(19, 6) NOT NULL,
    captured_at             TIMESTAMPTZ    NOT NULL,
    captured_by             UUID,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT         DEFAULT 0,
    CONSTRAINT uk_production_cost_baselines_work_order UNIQUE (work_order_id)
);

CREATE INDEX idx_production_cost_baselines_plant_id ON production_cost_baselines (plant_id);

CREATE TABLE production_cost_baseline_materials (
    baseline_material_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    baseline_id          UUID           NOT NULL REFERENCES production_cost_baselines (baseline_id),
    line_no              INTEGER        NOT NULL,
    component_line_id    UUID           NOT NULL REFERENCES work_order_component_lines (component_line_id),
    item_id              UUID           NOT NULL REFERENCES items (item_id),
    warehouse_id         UUID REFERENCES warehouses (warehouse_id),
    uom                  VARCHAR(50)    NOT NULL,
    planned_quantity     NUMERIC(19, 6) NOT NULL,
    -- Standard cost of one unit at release, up to 12 decimals (FE Q4); NULL = no standard cost was configured.
    unit_cost            NUMERIC(25, 12),
    planned_amount       NUMERIC(19, 6),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT         DEFAULT 0,
    CONSTRAINT uk_production_cost_baseline_materials_line UNIQUE (baseline_id, line_no),
    CONSTRAINT chk_production_cost_baseline_materials_known CHECK ((unit_cost IS NULL) = (planned_amount IS NULL))
);

CREATE TABLE production_cost_baseline_operations (
    baseline_operation_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    baseline_id            UUID           NOT NULL REFERENCES production_cost_baselines (baseline_id),
    work_order_operation_id UUID          NOT NULL REFERENCES work_order_operations (work_order_operation_id),
    sequence               INTEGER        NOT NULL,
    name                   VARCHAR(255)   NOT NULL,
    work_center_id         UUID REFERENCES work_centers (work_center_id),
    capacity_units         INTEGER,
    -- (setup + run × planned quantity) / capacity units, up to 12 decimals; NULL without a work center.
    planned_minutes        NUMERIC(25, 12),
    rate_id                UUID REFERENCES production_cost_rates (rate_id),
    rate_version           INTEGER,
    labor_rate             NUMERIC(19, 6),
    overhead_rate          NUMERIC(19, 6),
    planned_labor          NUMERIC(19, 6),
    planned_overhead       NUMERIC(19, 6),
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    version                BIGINT         DEFAULT 0,
    CONSTRAINT uk_production_cost_baseline_operations_operation UNIQUE (baseline_id, work_order_operation_id),
    CONSTRAINT chk_production_cost_baseline_operations_rate CHECK ((rate_id IS NULL) = (labor_rate IS NULL))
);

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_PRODUCTION_COST_CALCULATE', 'PRODUCTION_COST', 'CALCULATE',
     'Calculate production cost previews of work orders and read their cost'),
    ('PERM_COST_RATE_MANAGE', 'COST_RATE', 'MANAGE', 'Set the hourly labor and overhead rates of work centers'),
    ('PERM_WORK_ORDER_CLOSE', 'WORK_ORDER', 'CLOSE', 'Finalize the production cost of a work order and close it')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('ADMIN', 'MANAGER')
  AND p.code IN ('PERM_PRODUCTION_COST_CALCULATE', 'PERM_COST_RATE_MANAGE', 'PERM_WORK_ORDER_CLOSE')
ON CONFLICT DO NOTHING;
