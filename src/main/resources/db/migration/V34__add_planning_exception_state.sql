-- V34__add_planning_exception_state.sql
-- F5-B: planning reshape. Requirement lines record where their netting settings came from and how
-- many lots were skipped; supply suggestions carry the READY/WARNING/BLOCKED classification plus the
-- reason codes behind it (spec §2.4, §8.1).

ALTER TABLE mrp_requirement_lines
    ADD COLUMN setting_source     VARCHAR(30) NOT NULL DEFAULT 'SYSTEM_DEFAULT',
    ADD COLUMN excluded_lot_count INTEGER     NOT NULL DEFAULT 0;

-- Existing rows keep the defaults on purpose: a completed run is an immutable snapshot (spec §2.1),
-- and SYSTEM_DEFAULT/0 is exactly what those runs behaved as before this column existed.
ALTER TABLE mrp_requirement_lines
    ADD CONSTRAINT chk_mrp_req_setting_source
        CHECK (setting_source IN ('ITEM_WAREHOUSE', 'SYSTEM_DEFAULT')),
    ADD CONSTRAINT chk_mrp_req_excluded_lot_count
        CHECK (excluded_lot_count >= 0);

ALTER TABLE supply_suggestions
    ADD COLUMN exception_state VARCHAR(20)  NOT NULL DEFAULT 'READY',
    ADD COLUMN message_codes   VARCHAR(200);

ALTER TABLE supply_suggestions
    ADD CONSTRAINT chk_supply_suggestions_exception_state
        CHECK (exception_state IN ('READY', 'WARNING', 'BLOCKED'));

CREATE INDEX idx_supply_suggestions_run_exception_state
    ON supply_suggestions(mrp_run_id, exception_state);
