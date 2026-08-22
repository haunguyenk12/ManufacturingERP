-- Human-readable entity label captured at the time of the audited action.
-- Historical rows remain NULL because resolving a current live name would falsify the old snapshot.
ALTER TABLE audit_logs
    ADD COLUMN entity_name VARCHAR(255);

COMMENT ON COLUMN audit_logs.entity_name IS
    'Human-readable entity name/code/number snapshot at the time of the audited action';
