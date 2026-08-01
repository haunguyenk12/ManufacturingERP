-- V25: Store a payload fingerprint alongside every Idempotency-Key.
--
-- Until now a replay was matched on the key alone, so re-sending the same key with a DIFFERENT
-- body silently returned the original document and discarded the new payload. The frontend
-- contract requires that case to fail with IDEMPOTENCY_CONFLICT (409) instead.
--
-- Nullable on purpose: rows written before this migration have no fingerprint, and movements
-- created from derived child keys (":L<n>") carry the parent document's payload, not their own.
-- IdempotencySupport.ensureSamePayload treats NULL as "cannot verify" and allows the replay,
-- so existing documents keep replaying exactly as they do today.

ALTER TABLE stock_movements
    ADD COLUMN payload_hash VARCHAR(64);

ALTER TABLE material_issues
    ADD COLUMN payload_hash VARCHAR(64);

ALTER TABLE production_receipts
    ADD COLUMN payload_hash VARCHAR(64);

ALTER TABLE goods_receipts
    ADD COLUMN payload_hash VARCHAR(64);
