-- CF-5 (FE_REQUEST_CAPACITY_RELEASE_ALIGNMENT): release now schedules with the work center's capacityUnits, the same
-- walk the plant capacity preview uses. What release used is captured on the operation, so changing the work center
-- afterwards neither moves the committed schedule nor makes it unexplainable.
--
-- No backfill: an operation scheduled before this migration has NULL, meaning "not captured" — its schedule was
-- computed without dividing by capacityUnits, so inventing a value now would describe a formula that never ran.

ALTER TABLE work_order_operations
    ADD COLUMN captured_capacity_units   INTEGER,
    ADD COLUMN required_resource_minutes NUMERIC(19, 6),
    ADD COLUMN active_elapsed_minutes    NUMERIC(19, 6);

ALTER TABLE work_order_operations
    ADD CONSTRAINT chk_wo_operations_captured_capacity CHECK (
        (captured_capacity_units IS NULL AND required_resource_minutes IS NULL AND active_elapsed_minutes IS NULL)
        OR (captured_capacity_units IS NOT NULL AND required_resource_minutes IS NOT NULL
            AND active_elapsed_minutes IS NOT NULL
            AND captured_capacity_units > 0 AND required_resource_minutes >= 0 AND active_elapsed_minutes >= 0));
