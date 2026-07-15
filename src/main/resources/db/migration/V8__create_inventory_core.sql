-- V8__create_inventory_core.sql
-- Inventory item master, lots, stock balances, and append-only movements.

CREATE TABLE items (
    item_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID         NOT NULL REFERENCES companies(company_id),
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    unit        VARCHAR(30)  NOT NULL,
    lot_tracked BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       DEFAULT 0,
    CONSTRAINT uk_items_company_code UNIQUE (company_id, code),
    CONSTRAINT chk_items_type CHECK (type IN ('RAW_MATERIAL', 'WIP', 'FINISHED_GOOD', 'CONSUMABLE', 'SERVICE')),
    CONSTRAINT chk_items_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE inventory_lots (
    lot_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id     UUID         NOT NULL REFERENCES items(item_id),
    lot_code    VARCHAR(120) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    received_at TIMESTAMPTZ,
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       DEFAULT 0,
    CONSTRAINT uk_inventory_lots_item_code UNIQUE (item_id, lot_code),
    CONSTRAINT chk_inventory_lots_status CHECK (status IN ('AVAILABLE', 'HOLD', 'REJECTED', 'EXPIRED'))
);

CREATE TABLE stock_balances (
    balance_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id      UUID          NOT NULL REFERENCES items(item_id),
    warehouse_id UUID          NOT NULL REFERENCES warehouses(warehouse_id),
    lot_id       UUID          REFERENCES inventory_lots(lot_id),
    quantity     NUMERIC(19,6) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT        DEFAULT 0,
    CONSTRAINT chk_stock_balances_quantity CHECK (quantity >= 0)
);

CREATE UNIQUE INDEX uk_stock_balances_item_warehouse_no_lot
    ON stock_balances(item_id, warehouse_id)
    WHERE lot_id IS NULL;

CREATE UNIQUE INDEX uk_stock_balances_item_warehouse_lot
    ON stock_balances(item_id, warehouse_id, lot_id)
    WHERE lot_id IS NOT NULL;

CREATE TABLE stock_movements (
    movement_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id         UUID          NOT NULL REFERENCES items(item_id),
    warehouse_id    UUID          NOT NULL REFERENCES warehouses(warehouse_id),
    lot_id          UUID          REFERENCES inventory_lots(lot_id),
    movement_type   VARCHAR(30)   NOT NULL,
    direction       VARCHAR(10)   NOT NULL,
    quantity        NUMERIC(19,6) NOT NULL,
    reason          TEXT,
    reference_type  VARCHAR(80),
    reference_id    VARCHAR(120),
    idempotency_key VARCHAR(120)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by      UUID,
    CONSTRAINT uk_stock_movements_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_stock_movements_type CHECK (movement_type IN ('RECEIVE', 'ISSUE', 'ADJUST_IN', 'ADJUST_OUT')),
    CONSTRAINT chk_stock_movements_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_stock_movements_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_items_company_id ON items(company_id);
CREATE INDEX idx_items_status ON items(status);
CREATE INDEX idx_inventory_lots_item_id ON inventory_lots(item_id);
CREATE INDEX idx_inventory_lots_status ON inventory_lots(status);
CREATE INDEX idx_stock_balances_warehouse_id ON stock_balances(warehouse_id);
CREATE INDEX idx_stock_balances_item_id ON stock_balances(item_id);
CREATE INDEX idx_stock_balances_lot_id ON stock_balances(lot_id);
CREATE INDEX idx_stock_movements_warehouse_item ON stock_movements(warehouse_id, item_id, created_at DESC);
CREATE INDEX idx_stock_movements_lot_id ON stock_movements(lot_id);
CREATE INDEX idx_stock_movements_created_at ON stock_movements(created_at DESC);
