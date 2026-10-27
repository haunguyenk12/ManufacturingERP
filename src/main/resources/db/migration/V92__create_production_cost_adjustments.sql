-- G11c (FE handoff GĐ11): value-only cost entries written by the atomic finalize-and-close command. Quantity is always
-- 0: the stock rows never change at close (FE §3.6), only the value attributed to the work order's own output is
-- recorded — the part still on hand (stock_value_delta = allocated_amount) and the part already delivered before close
-- (stock_value_delta = 0, allocated_amount = its cost of goods, linked to the delivery line and its ISSUE). The
-- original movements are never rewritten (FE R20). No general value ledger exists (D1, D3).

CREATE TABLE production_cost_adjustments (
    adjustment_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id               UUID           NOT NULL REFERENCES production_cost_runs (run_id),
    work_order_id        UUID           NOT NULL REFERENCES work_orders (work_order_id),
    company_id           UUID           NOT NULL REFERENCES companies (company_id),
    plant_id             UUID           NOT NULL REFERENCES plants (plant_id),
    item_id              UUID           NOT NULL REFERENCES items (item_id),
    warehouse_id         UUID           NOT NULL REFERENCES warehouses (warehouse_id),
    line_no              INTEGER        NOT NULL,
    receipt_id           UUID           NOT NULL REFERENCES production_receipts (receipt_id),
    decision_id          UUID REFERENCES production_quality_decisions (decision_id),
    quality              VARCHAR(20)    NOT NULL,
    lot_id               UUID REFERENCES inventory_lots (lot_id),
    delivery_id          UUID,
    delivery_line_id     UUID,
    adjusted_movement_id UUID REFERENCES stock_movements (movement_id),
    quantity             NUMERIC(19, 6) NOT NULL DEFAULT 0,
    -- The physical quantity the value covers: still on hand (stock entry) or delivered (delivery entry).
    covered_quantity     NUMERIC(19, 6) NOT NULL,
    stock_value_delta    NUMERIC(19, 6) NOT NULL,
    allocated_amount     NUMERIC(19, 6) NOT NULL,
    stock_value_before   NUMERIC(19, 6),
    stock_value_after    NUMERIC(19, 6),
    reason               TEXT           NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT         DEFAULT 0,
    CONSTRAINT uk_production_cost_adjustments_run_line UNIQUE (run_id, line_no),
    CONSTRAINT chk_production_cost_adjustments_quantity CHECK (quantity = 0),
    CONSTRAINT chk_production_cost_adjustments_quality CHECK (quality IN ('ACCEPTED', 'REJECTED', 'PENDING')),
    CONSTRAINT chk_production_cost_adjustments_kind CHECK (
        (delivery_line_id IS NULL AND stock_value_delta = allocated_amount)
        OR (delivery_line_id IS NOT NULL AND stock_value_delta = 0))
);

CREATE INDEX idx_production_cost_adjustments_run ON production_cost_adjustments (run_id);
CREATE INDEX idx_production_cost_adjustments_delivery_line ON production_cost_adjustments (delivery_line_id);
CREATE INDEX idx_production_cost_adjustments_lot ON production_cost_adjustments (lot_id);
