-- V16__create_manufacturing_execution_v1.sql
-- Manufacturing Execution v1: reservations, material issue docs, WIP events, receipts, and variance sources.

ALTER TABLE stock_balances
    ADD COLUMN reserved_quantity NUMERIC(19,6) NOT NULL DEFAULT 0;

ALTER TABLE stock_balances
    ADD CONSTRAINT chk_stock_balances_reserved_quantity CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity);

CREATE TABLE material_reservations (
    reservation_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id      UUID          NOT NULL REFERENCES work_orders(work_order_id),
    component_line_id  UUID          NOT NULL REFERENCES work_order_component_lines(component_line_id),
    item_id            UUID          NOT NULL REFERENCES items(item_id),
    warehouse_id       UUID          NOT NULL REFERENCES warehouses(warehouse_id),
    lot_id             UUID          REFERENCES inventory_lots(lot_id),
    quantity           NUMERIC(19,6) NOT NULL,
    consumed_quantity  NUMERIC(19,6) NOT NULL DEFAULT 0,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT        DEFAULT 0,
    CONSTRAINT chk_material_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT chk_material_reservations_consumed CHECK (consumed_quantity >= 0 AND consumed_quantity <= quantity),
    CONSTRAINT chk_material_reservations_status CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED', 'CANCELLED'))
);

CREATE TABLE material_issues (
    issue_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id   UUID          NOT NULL REFERENCES work_orders(work_order_id),
    status          VARCHAR(20)   NOT NULL DEFAULT 'POSTED',
    idempotency_key VARCHAR(120)  NOT NULL,
    posted_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    note            TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT        DEFAULT 0,
    CONSTRAINT uk_material_issues_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_material_issues_status CHECK (status IN ('POSTED', 'CANCELLED'))
);

CREATE TABLE material_issue_lines (
    issue_line_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id         UUID          NOT NULL REFERENCES material_issues(issue_id) ON DELETE CASCADE,
    component_line_id UUID         NOT NULL REFERENCES work_order_component_lines(component_line_id),
    reservation_id   UUID          REFERENCES material_reservations(reservation_id),
    item_id          UUID          NOT NULL REFERENCES items(item_id),
    warehouse_id     UUID          NOT NULL REFERENCES warehouses(warehouse_id),
    lot_id           UUID          REFERENCES inventory_lots(lot_id),
    quantity         NUMERIC(19,6) NOT NULL,
    stock_movement_id UUID         NOT NULL REFERENCES stock_movements(movement_id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT        DEFAULT 0,
    CONSTRAINT chk_material_issue_lines_quantity CHECK (quantity > 0)
);

CREATE TABLE wip_transactions (
    wip_transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id      UUID          NOT NULL REFERENCES work_orders(work_order_id),
    transaction_type   VARCHAR(40)   NOT NULL,
    stage_code         VARCHAR(80),
    quantity           NUMERIC(19,6),
    reference_type     VARCHAR(80),
    reference_id       VARCHAR(120),
    occurred_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    note               TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT        DEFAULT 0,
    CONSTRAINT chk_wip_transactions_type CHECK (transaction_type IN ('START', 'MATERIAL_ISSUED', 'OUTPUT_COMPLETED', 'SCRAP_REPORTED', 'REWORK_REPORTED')),
    CONSTRAINT chk_wip_transactions_quantity CHECK (quantity IS NULL OR quantity > 0)
);

CREATE TABLE production_receipts (
    receipt_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id   UUID          NOT NULL REFERENCES work_orders(work_order_id),
    status          VARCHAR(20)   NOT NULL DEFAULT 'POSTED',
    idempotency_key VARCHAR(120)  NOT NULL,
    posted_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    note            TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT        DEFAULT 0,
    CONSTRAINT uk_production_receipts_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_production_receipts_status CHECK (status IN ('POSTED', 'CANCELLED'))
);

CREATE TABLE production_receipt_lines (
    receipt_line_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id       UUID          NOT NULL REFERENCES production_receipts(receipt_id) ON DELETE CASCADE,
    item_id          UUID          NOT NULL REFERENCES items(item_id),
    warehouse_id     UUID          NOT NULL REFERENCES warehouses(warehouse_id),
    lot_id           UUID          REFERENCES inventory_lots(lot_id),
    quantity         NUMERIC(19,6) NOT NULL,
    stock_movement_id UUID         NOT NULL REFERENCES stock_movements(movement_id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT        DEFAULT 0,
    CONSTRAINT chk_production_receipt_lines_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_stock_balances_reserved_quantity ON stock_balances(reserved_quantity);
CREATE INDEX idx_material_reservations_work_order_id ON material_reservations(work_order_id);
CREATE INDEX idx_material_reservations_component_line_id ON material_reservations(component_line_id);
CREATE INDEX idx_material_reservations_item_id ON material_reservations(item_id);
CREATE INDEX idx_material_reservations_warehouse_id ON material_reservations(warehouse_id);
CREATE INDEX idx_material_reservations_lot_id ON material_reservations(lot_id);
CREATE INDEX idx_material_reservations_status ON material_reservations(status);
CREATE INDEX idx_material_issues_work_order_id ON material_issues(work_order_id);
CREATE INDEX idx_material_issues_status ON material_issues(status);
CREATE INDEX idx_material_issues_posted_at ON material_issues(posted_at DESC);
CREATE INDEX idx_material_issue_lines_issue_id ON material_issue_lines(issue_id);
CREATE INDEX idx_material_issue_lines_component_line_id ON material_issue_lines(component_line_id);
CREATE INDEX idx_material_issue_lines_reservation_id ON material_issue_lines(reservation_id);
CREATE INDEX idx_material_issue_lines_stock_movement_id ON material_issue_lines(stock_movement_id);
CREATE INDEX idx_wip_transactions_work_order_id ON wip_transactions(work_order_id);
CREATE INDEX idx_wip_transactions_type ON wip_transactions(transaction_type);
CREATE INDEX idx_wip_transactions_occurred_at ON wip_transactions(occurred_at DESC);
CREATE INDEX idx_production_receipts_work_order_id ON production_receipts(work_order_id);
CREATE INDEX idx_production_receipts_status ON production_receipts(status);
CREATE INDEX idx_production_receipts_posted_at ON production_receipts(posted_at DESC);
CREATE INDEX idx_production_receipt_lines_receipt_id ON production_receipt_lines(receipt_id);
CREATE INDEX idx_production_receipt_lines_stock_movement_id ON production_receipt_lines(stock_movement_id);
