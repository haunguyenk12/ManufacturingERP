-- V72__create_stock_transfers.sql
-- G4-2 (FE_HANDOFF_GD_04_2026-10-12.md): atomic warehouse-to-warehouse transfer inside one plant.
--
-- Purely additive. A transfer is a posted document — there is no draft, approval, edit, cancel or
-- in-transit state in G4 (NEXT_PHASE_PLAN.md §11.2 D2), so there is no status column: every row is
-- POSTED by construction, the same reasoning as production_executions in F10.
--
-- Each line moves stock with a pair of ledger rows (TRANSFER_OUT at the source, TRANSFER_IN at the
-- destination) written in the same transaction as the document. The lot row is shared by both
-- warehouses, so lot code, received date and expiry travel with the stock without being copied.

CREATE TABLE stock_transfers (
    transfer_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 'TR-' || upper(substring(transfer_id::text, 1, 8)), assigned in @PrePersist (B68 lesson).
    code              VARCHAR(40)   NOT NULL,
    plant_id          UUID          NOT NULL REFERENCES plants (plant_id),
    from_warehouse_id UUID          NOT NULL REFERENCES warehouses (warehouse_id),
    to_warehouse_id   UUID          NOT NULL REFERENCES warehouses (warehouse_id),
    reason            TEXT          NOT NULL,
    posted_at         TIMESTAMPTZ   NOT NULL,
    idempotency_key   VARCHAR(120)  NOT NULL,
    payload_hash      VARCHAR(64),
    trace_id          VARCHAR(64),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT        DEFAULT 0,
    CONSTRAINT uk_stock_transfers_code UNIQUE (code),
    CONSTRAINT uk_stock_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_stock_transfers_distinct_warehouses CHECK (from_warehouse_id <> to_warehouse_id)
);

CREATE INDEX idx_stock_transfers_plant_posted_at ON stock_transfers (plant_id, posted_at DESC);
CREATE INDEX idx_stock_transfers_from_warehouse_id ON stock_transfers (from_warehouse_id);
CREATE INDEX idx_stock_transfers_to_warehouse_id ON stock_transfers (to_warehouse_id);

CREATE TABLE stock_transfer_lines (
    transfer_line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id      UUID          NOT NULL REFERENCES stock_transfers (transfer_id),
    line_no          INTEGER       NOT NULL,
    item_id          UUID          NOT NULL REFERENCES items (item_id),
    lot_id           UUID          REFERENCES inventory_lots (lot_id),
    quantity         NUMERIC(19,6) NOT NULL,
    out_movement_id  UUID          NOT NULL REFERENCES stock_movements (movement_id),
    in_movement_id   UUID          NOT NULL REFERENCES stock_movements (movement_id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT        DEFAULT 0,
    CONSTRAINT uk_stock_transfer_lines_transfer_line_no UNIQUE (transfer_id, line_no),
    CONSTRAINT chk_stock_transfer_lines_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_stock_transfer_lines_transfer_id ON stock_transfer_lines (transfer_id);
CREATE INDEX idx_stock_transfer_lines_item_id ON stock_transfer_lines (item_id);

-- coding-rules.md §11.3: a new MovementType is a breaking change for this CHECK; V26 already shipped
-- one value the constraint forgot (REVERSAL), so the full list is restated here.
ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_type
    CHECK (movement_type IN ('RECEIVE','ISSUE','ADJUST_IN','ADJUST_OUT','REVERSAL','LOT_STATUS_CHANGE',
                             'TRANSFER_OUT','TRANSFER_IN'));
