-- P5: Serial Number Tracking. Mirrors inventory_lots (V8), but a serial always represents
-- exactly one physical unit, never a fungible quantity.

CREATE TABLE serial_numbers (
    serial_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id       UUID         NOT NULL REFERENCES items(item_id),
    serial_code   VARCHAR(120) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    received_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT       DEFAULT 0,
    CONSTRAINT uk_serial_numbers_item_code UNIQUE (item_id, serial_code),
    CONSTRAINT chk_serial_numbers_status CHECK (status IN ('AVAILABLE', 'REJECTED', 'ISSUED'))
);

CREATE INDEX idx_serial_numbers_item_id ON serial_numbers(item_id);
CREATE INDEX idx_serial_numbers_status ON serial_numbers(status);

ALTER TABLE items ADD COLUMN serial_tracked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE items ADD CONSTRAINT chk_items_tracking_exclusive CHECK (NOT (lot_tracked AND serial_tracked));

ALTER TABLE stock_movements ADD COLUMN serial_id UUID REFERENCES serial_numbers(serial_id);
CREATE INDEX idx_stock_movements_serial_id ON stock_movements(serial_id);

ALTER TABLE material_issue_lines ADD COLUMN serial_id UUID REFERENCES serial_numbers(serial_id);

ALTER TABLE production_receipt_lines ADD COLUMN serial_id UUID REFERENCES serial_numbers(serial_id);
ALTER TABLE production_receipt_lines ADD COLUMN requested_serial_code VARCHAR(120);
