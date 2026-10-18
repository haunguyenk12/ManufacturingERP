-- V75__add_commercial_planning_runs_and_conversion_ledger.sql
-- G6b (FE_HANDOFF_GD_06_2026-10-14.md): commercial MRP run over sales order lines, and a ledger of
-- partial conversions from a planned requirement to draft work orders / purchase orders.
--
-- Purely additive. Existing runs become run_kind = 'STANDARD' with no source version and no
-- conversion history; NULL source_version means "run made before G6b", not "nothing to compare".
-- Nothing is backfilled.

ALTER TABLE mrp_runs
    ADD COLUMN run_kind           VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    -- sha-256 (hex) of the sources the run was computed from (sales order lines, active BOMs and
    -- routings of every manufactured item). Opaque to clients; compared on every conversion.
    ADD COLUMN source_version     VARCHAR(64),
    -- Bumped by each successful conversion request; the client echoes it as expectedConversionVersion.
    ADD COLUMN conversion_version INTEGER     NOT NULL DEFAULT 0,
    -- The validated commercial request (sales order lines, per-item warehouse mappings).
    ADD COLUMN input_snapshot     JSONB;

ALTER TABLE mrp_runs
    ADD CONSTRAINT chk_mrp_runs_run_kind CHECK (run_kind IN ('STANDARD', 'COMMERCIAL')),
    ADD CONSTRAINT chk_mrp_runs_conversion_version CHECK (conversion_version >= 0);

-- coding-rules.md §11.3: the new WarehouseResolutionSource value must be accepted by the CHECK.
ALTER TABLE mrp_requirement_lines DROP CONSTRAINT chk_mrp_req_warehouse_resolution_source;
ALTER TABLE mrp_requirement_lines
    ADD CONSTRAINT chk_mrp_req_warehouse_resolution_source CHECK (warehouse_resolution_source IN (
        'DEMAND_WAREHOUSE','RUN_DEMAND_WAREHOUSE','RUN_ITEM_MAPPING','SINGLE_ACTIVE_WAREHOUSE',
        'ITEM_WAREHOUSE_DEFAULT','ITEM_WAREHOUSE_ONLY','WAREHOUSE_TYPE_FALLBACK','UNRESOLVED'
    ));

-- One row per converted entry. remainingConvertible = suggested_quantity - SUM(quantity).
-- A request that converts several entries shares one conversion_batch_id.
CREATE TABLE supply_suggestion_conversions (
    conversion_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mrp_run_id            UUID          NOT NULL REFERENCES mrp_runs (mrp_run_id),
    supply_suggestion_id  UUID          NOT NULL REFERENCES supply_suggestions (supply_suggestion_id),
    requirement_line_id   UUID          NOT NULL REFERENCES mrp_requirement_lines (mrp_requirement_line_id),
    conversion_batch_id   UUID          NOT NULL,
    entry_no              INTEGER       NOT NULL,
    quantity              NUMERIC(19,6) NOT NULL,
    target_type           VARCHAR(30)   NOT NULL,
    target_id             UUID          NOT NULL,
    target_line_id        UUID,
    target_code           VARCHAR(100)  NOT NULL,
    -- Run conversion version reached by the request that wrote this row.
    conversion_version    INTEGER       NOT NULL,
    idempotency_key       VARCHAR(120),
    payload_hash          VARCHAR(64),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT        DEFAULT 0,
    CONSTRAINT chk_supply_suggestion_conversions_quantity CHECK (quantity > 0),
    CONSTRAINT chk_supply_suggestion_conversions_target_type CHECK (target_type IN ('WORK_ORDER', 'PURCHASE_ORDER')),
    -- A replayed Idempotency-Key finds these rows; NULL keys never collide.
    CONSTRAINT uk_supply_suggestion_conversions_run_key_entry UNIQUE (mrp_run_id, idempotency_key, entry_no)
);

CREATE INDEX idx_supply_suggestion_conversions_suggestion_id ON supply_suggestion_conversions (supply_suggestion_id);
CREATE INDEX idx_supply_suggestion_conversions_run_created ON supply_suggestion_conversions (mrp_run_id, created_at);
CREATE INDEX idx_supply_suggestion_conversions_requirement_line_id ON supply_suggestion_conversions (requirement_line_id);
