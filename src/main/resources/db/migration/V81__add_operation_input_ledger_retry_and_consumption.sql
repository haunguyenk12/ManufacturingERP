-- G8c: operations of a work order — input / transfer ledger, retry genealogy, material consumption, completion.
--
--   * work_orders.operation_tracking: TRUE when every execution of the work order went through the operation ledger
--     (inputs, pools, consumption). Work orders that already have executions keep FALSE: their inputs and consumption
--     were never recorded, so the server reports them as unknown instead of inventing 0 or "everything issued"
--     (FE Q22). They keep the pre-G8c reporting rules.
--   * work_orders.terminal_shortfall_quantity / shortfall_reason: captured by complete-production when the final good
--     is below the plan (FE R14, Q4). NULL = not completed through that command (or no shortfall recorded).
--   * work_order_operations.completed_at / completed_by: the explicit "finish" of an operation (FE R14). Everything
--     else about an operation is derived from the two ledgers below and from production_executions — one source of
--     truth, no counter columns that could drift from it.
--   * production_executions.source_execution_id: a retry of rework from an earlier execution of the same operation
--     (FE R11, D7). What a source has left = its rework minus everything recorded against it.
--   * work_order_operation_inputs: confirmed input of an operation. The first operation's input comes from the plan
--     (source_operation_id NULL); a later operation's input is a transfer of its predecessor's good output.
--   * production_execution_consumptions: material actually used by an execution, per posted issue line. Trace only —
--     no stock posting (the stock left the warehouse when it was issued).

ALTER TABLE work_orders
    ADD COLUMN operation_tracking          BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN terminal_shortfall_quantity NUMERIC(19, 6),
    ADD COLUMN shortfall_reason            TEXT;

ALTER TABLE work_orders
    ADD CONSTRAINT chk_work_orders_terminal_shortfall
        CHECK (terminal_shortfall_quantity IS NULL OR terminal_shortfall_quantity >= 0);

UPDATE work_orders w
SET operation_tracking = FALSE
WHERE EXISTS (SELECT 1 FROM production_executions pe WHERE pe.work_order_id = w.work_order_id);

ALTER TABLE work_order_operations
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN completed_by UUID;

ALTER TABLE production_executions
    ADD COLUMN source_execution_id UUID REFERENCES production_executions (production_execution_id);

CREATE INDEX idx_production_executions_source_execution_id ON production_executions (source_execution_id);

CREATE TABLE work_order_operation_inputs (
    input_id                UUID PRIMARY KEY,
    work_order_id           UUID           NOT NULL REFERENCES work_orders (work_order_id),
    work_order_operation_id UUID           NOT NULL REFERENCES work_order_operations (work_order_operation_id),
    source_operation_id     UUID REFERENCES work_order_operations (work_order_operation_id),
    quantity                NUMERIC(19, 6) NOT NULL,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_operation_inputs_quantity CHECK (quantity > 0),
    CONSTRAINT chk_operation_inputs_not_self CHECK (source_operation_id IS NULL OR source_operation_id <> work_order_operation_id)
);

CREATE INDEX idx_operation_inputs_work_order ON work_order_operation_inputs (work_order_id);
CREATE INDEX idx_operation_inputs_operation ON work_order_operation_inputs (work_order_operation_id);

CREATE TABLE production_execution_consumptions (
    consumption_id          UUID PRIMARY KEY,
    production_execution_id UUID           NOT NULL REFERENCES production_executions (production_execution_id),
    issue_line_id           UUID           NOT NULL REFERENCES material_issue_lines (issue_line_id),
    quantity                NUMERIC(19, 6) NOT NULL,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_execution_consumptions_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_execution_consumptions_execution ON production_execution_consumptions (production_execution_id);
CREATE INDEX idx_execution_consumptions_issue_line ON production_execution_consumptions (issue_line_id);
