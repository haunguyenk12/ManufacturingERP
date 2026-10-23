-- G9b: which shop-floor reports each production receipt draws from (FE_HANDOFF_GD_09 R05/R13/Q6, NEXT_PHASE_PLAN.md §16 D3).
--
-- One row per (receipt, final-good execution) with the quantity the receipt claims from that execution. The server
-- allocates them, oldest report first, at creation and on every draft edit; a client never names its sources. A
-- receipt claims output while it is DRAFT, PENDING_APPROVAL or APPROVED; a rejected or cancelled receipt keeps its
-- rows as history but no longer counts.
--
-- Data: nothing is backfilled. Receipts written before this migration have no row and report their source as
-- unknown (outputEvidenceKnown = false) — deriving one now would be inventing evidence nobody recorded (D3, R22).
-- Their quantity still counts against the work order's output, oldest reports first, when a new receipt is allocated.

CREATE TABLE production_receipt_contributions (
    contribution_id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id               UUID           NOT NULL REFERENCES production_receipts (receipt_id),
    production_execution_id  UUID           NOT NULL REFERENCES production_executions (production_execution_id),
    quantity                 NUMERIC(19, 6) NOT NULL,
    created_at               TIMESTAMPTZ    NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL,
    created_by               UUID,
    updated_by               UUID,
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_production_receipt_contributions_receipt_execution UNIQUE (receipt_id, production_execution_id),
    CONSTRAINT chk_production_receipt_contributions_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_production_receipt_contributions_execution ON production_receipt_contributions (production_execution_id);
