-- V37 (D6): Scope the stock_movements Idempotency-Key by movement type.
--
-- V8 declared UNIQUE (idempotency_key) across the WHOLE table, and the replay lookup in
-- InventoryMovementService matched on the key alone. One key therefore covered every operation:
--
--   POST /receive  Idempotency-Key: K   ->  creates a RECEIVE movement
--   POST /issue    Idempotency-Key: K   ->  returns that RECEIVE movement, issues nothing
--
-- The client saw 200/201 with a document belonging to a DIFFERENT business operation (debt #10).
-- Spec §10.2 wants the key scoped per operation; movement_type is exactly that scope here.
--
-- Deliberately NOT including created_by: that column is nullable (V8), and Postgres treats two
-- NULLs as distinct in a UNIQUE constraint, so every historical row with created_by IS NULL would
-- lose duplicate protection entirely. Scoping by user needs a backfill first — out of D6's scope.
--
-- No backfill needed: widening the key set can only reduce collisions, so existing rows stay valid
-- and this migration cannot fail on duplicates.
--
-- Note: issue() and issueReserved() both write MovementType.ISSUE on purpose — both are "goods out".
-- This constraint does not separate them, and it must not: they are the same operation.

ALTER TABLE stock_movements
    DROP CONSTRAINT uk_stock_movements_idempotency_key;

ALTER TABLE stock_movements
    ADD CONSTRAINT uk_stock_movements_idempotency_key
        UNIQUE (idempotency_key, movement_type);
