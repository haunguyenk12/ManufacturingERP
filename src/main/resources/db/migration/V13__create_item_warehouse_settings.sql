-- V13__create_item_warehouse_settings.sql
-- Item + warehouse planning thresholds for low-stock and reorder alerts.

CREATE TABLE item_warehouse_settings (
    setting_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id        UUID          NOT NULL REFERENCES items(item_id),
    warehouse_id   UUID          NOT NULL REFERENCES warehouses(warehouse_id),
    safety_stock   NUMERIC(19,6) NOT NULL DEFAULT 0,
    reorder_point  NUMERIC(19,6) NOT NULL DEFAULT 0,
    lead_time_days INTEGER       NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT        DEFAULT 0,
    CONSTRAINT chk_item_wh_settings_safety_stock CHECK (safety_stock >= 0),
    CONSTRAINT chk_item_wh_settings_reorder_point CHECK (reorder_point >= 0),
    CONSTRAINT chk_item_wh_settings_lead_time_days CHECK (lead_time_days >= 0),
    CONSTRAINT chk_item_wh_settings_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_item_wh_settings_active
    ON item_warehouse_settings(item_id, warehouse_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_item_wh_settings_item_id ON item_warehouse_settings(item_id);
CREATE INDEX idx_item_wh_settings_warehouse_id ON item_warehouse_settings(warehouse_id);
CREATE INDEX idx_item_wh_settings_status ON item_warehouse_settings(status);
