-- G8a: work order drafts — a warehouse per component line, the committed schedule's context, and
-- Idempotency-Key for work order commands.
--
--   * work_order_component_lines.warehouse_id: the one warehouse a component is reserved from (D2).
--     NULL keeps the behaviour every existing line has — reserve across every warehouse of the
--     plant. Not backfilled: there is no truthful value to put there for history.
--   * work_orders.schedule_*: what the schedule committed at release was computed against (D9).
--     schedule_revision 0 means "no schedule committed yet"; it moves on at every release and every
--     manual schedule adjustment. The calendar revision is a content fingerprint of the calendars
--     consulted, the time zone is the plant's at that moment (a plant's time zone can be edited).
--   * work_order_idempotency_records: the commands change an existing work order rather than create
--     a ledger row, so the key cannot live on a document row (same reason as
--     purchasing_idempotency_records, V76).

ALTER TABLE work_order_component_lines
    ADD COLUMN warehouse_id UUID REFERENCES warehouses (warehouse_id);

CREATE INDEX idx_work_order_component_lines_warehouse_id ON work_order_component_lines (warehouse_id);

ALTER TABLE work_orders
    ADD COLUMN schedule_revision          INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN schedule_calendar_revision VARCHAR(64),
    ADD COLUMN schedule_time_zone         VARCHAR(64),
    ADD COLUMN schedule_captured_at       TIMESTAMPTZ;

ALTER TABLE work_orders
    ADD CONSTRAINT chk_work_orders_schedule_revision CHECK (schedule_revision >= 0);

CREATE TABLE work_order_idempotency_records (
    record_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(120) NOT NULL,
    command          VARCHAR(40)  NOT NULL,
    resource_id      UUID         NOT NULL,
    payload_hash     VARCHAR(64)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       DEFAULT 0,
    CONSTRAINT uk_work_order_idempotency_key_command UNIQUE (idempotency_key, command)
);

CREATE INDEX idx_work_order_idempotency_resource ON work_order_idempotency_records (resource_id);
