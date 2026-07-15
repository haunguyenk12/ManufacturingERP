-- V14__create_work_order_core.sql
-- Work order execution foundation using active BOM snapshots and inventory movements.

CREATE TABLE work_orders (
    work_order_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id         UUID          NOT NULL REFERENCES companies(company_id),
    plant_id           UUID          NOT NULL REFERENCES plants(plant_id),
    work_order_no      VARCHAR(100)  NOT NULL,
    product_item_id    UUID          NOT NULL REFERENCES items(item_id),
    bom_id             UUID          NOT NULL REFERENCES bom_headers(bom_id),
    bom_revision       VARCHAR(40)   NOT NULL,
    output_warehouse_id UUID         NOT NULL REFERENCES warehouses(warehouse_id),
    planned_quantity   NUMERIC(19,6) NOT NULL,
    completed_quantity NUMERIC(19,6) NOT NULL DEFAULT 0,
    status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    planned_start_at   TIMESTAMPTZ,
    planned_end_at     TIMESTAMPTZ,
    released_at        TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    cancelled_at       TIMESTAMPTZ,
    notes              TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT        DEFAULT 0,
    CONSTRAINT uk_work_orders_plant_no UNIQUE (plant_id, work_order_no),
    CONSTRAINT chk_work_orders_status CHECK (status IN ('DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_work_orders_planned_quantity CHECK (planned_quantity > 0),
    CONSTRAINT chk_work_orders_completed_quantity CHECK (completed_quantity >= 0 AND completed_quantity <= planned_quantity)
);

CREATE TABLE work_order_component_lines (
    component_line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id     UUID          NOT NULL REFERENCES work_orders(work_order_id) ON DELETE CASCADE,
    bom_line_id       UUID          NOT NULL REFERENCES bom_lines(line_id),
    component_item_id UUID          NOT NULL REFERENCES items(item_id),
    line_no           INTEGER       NOT NULL,
    quantity_per      NUMERIC(19,6) NOT NULL,
    scrap_rate        NUMERIC(9,6)  NOT NULL DEFAULT 0,
    required_quantity NUMERIC(19,6) NOT NULL,
    issued_quantity   NUMERIC(19,6) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT        DEFAULT 0,
    CONSTRAINT uk_work_order_component_line_no UNIQUE (work_order_id, line_no),
    CONSTRAINT uk_work_order_component_item UNIQUE (work_order_id, component_item_id),
    CONSTRAINT chk_work_order_component_quantity_per CHECK (quantity_per > 0),
    CONSTRAINT chk_work_order_component_scrap_rate CHECK (scrap_rate >= 0 AND scrap_rate < 1),
    CONSTRAINT chk_work_order_component_required CHECK (required_quantity > 0),
    CONSTRAINT chk_work_order_component_issued CHECK (issued_quantity >= 0 AND issued_quantity <= required_quantity)
);

CREATE INDEX idx_work_orders_company_id ON work_orders(company_id);
CREATE INDEX idx_work_orders_plant_id ON work_orders(plant_id);
CREATE INDEX idx_work_orders_product_item_id ON work_orders(product_item_id);
CREATE INDEX idx_work_orders_status ON work_orders(status);
CREATE INDEX idx_work_order_component_work_order_id ON work_order_component_lines(work_order_id);
CREATE INDEX idx_work_order_component_item_id ON work_order_component_lines(component_item_id);
