-- CF-4 (FE_REQUEST_MRP_PENDING_QC_VISIBILITY): purchase stock received but still awaiting QC is shown next to a
-- requirement, never netted. A run captures, once, which goods receipt lines held pending quantity of the
-- requirement's item and warehouse(s) when it ran; QC decided later never rewrites it (FE R05).
--
-- No backfill: a run before this migration has NULL pending columns, meaning "not captured", not zero (B77, V67).

ALTER TABLE mrp_requirement_lines
    ADD COLUMN pending_qc_quantity     NUMERIC(19, 6),
    ADD COLUMN pending_qc_source_count INTEGER;

-- Both or neither. The NOT NULL terms are load-bearing: without them `5 >= 0 AND NULL >= 0` is NULL, and a CHECK
-- lets NULL through, so a quantity without its source count would be accepted.
ALTER TABLE mrp_requirement_lines
    ADD CONSTRAINT chk_mrp_req_pending_qc CHECK (
        (pending_qc_quantity IS NULL AND pending_qc_source_count IS NULL)
        OR (pending_qc_quantity IS NOT NULL AND pending_qc_source_count IS NOT NULL
            AND pending_qc_quantity >= 0 AND pending_qc_source_count >= 0));

CREATE TABLE mrp_pending_qc_sources (
    source_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mrp_run_id            UUID           NOT NULL REFERENCES mrp_runs (mrp_run_id),
    requirement_line_id   UUID           NOT NULL REFERENCES mrp_requirement_lines (mrp_requirement_line_id),
    goods_receipt_id      UUID           NOT NULL REFERENCES goods_receipts (goods_receipt_id),
    goods_receipt_line_id UUID           NOT NULL REFERENCES goods_receipt_lines (goods_receipt_line_id),
    receipt_code          VARCHAR(100)   NOT NULL,
    receipt_version       BIGINT         NOT NULL,
    item_id               UUID           NOT NULL REFERENCES items (item_id),
    warehouse_id          UUID           NOT NULL REFERENCES warehouses (warehouse_id),
    uom_code              VARCHAR(30)    NOT NULL,
    pending_quantity      NUMERIC(19, 6) NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT         DEFAULT 0,
    CONSTRAINT uk_mrp_pending_qc_sources_line_receipt_line UNIQUE (requirement_line_id, goods_receipt_line_id),
    CONSTRAINT chk_mrp_pending_qc_sources_quantity CHECK (pending_quantity > 0)
);

CREATE INDEX idx_mrp_pending_qc_sources_run ON mrp_pending_qc_sources (mrp_run_id, receipt_code, goods_receipt_line_id);
CREATE INDEX idx_mrp_pending_qc_sources_requirement ON mrp_pending_qc_sources (requirement_line_id);
