-- G8d: material returns and the work-in-progress read model.
--
--   * material_returns / material_return_lines: unused material going back to the exact source it was issued from
--     (FE R16, D3). Each line names one posted issue line; the server resolves item, warehouse and lot from it — the
--     client never chooses them. A return is POSTED when written and never edited (FE Q36). It does not reduce the
--     gross issued quantity of the component (FE Q11) and does not restore a reservation (FE R17).
--   * wip_transactions: MATERIAL_RETURNED joins the ledger so it keeps every material movement of a work order.
--   * PERM_WIP_READ / PERM_MATERIAL_RETURN_READ / PERM_MATERIAL_RETURN_MANAGE for ADMIN and MANAGER only (FE Q34).
--
-- Data: nothing to backfill — no return existed before this migration.

CREATE TABLE material_returns (
    material_return_id UUID PRIMARY KEY,
    code               VARCHAR(40)  NOT NULL,
    work_order_id      UUID         NOT NULL REFERENCES work_orders (work_order_id),
    status             VARCHAR(20)  NOT NULL DEFAULT 'POSTED',
    reason             TEXT         NOT NULL,
    posted_at          TIMESTAMPTZ  NOT NULL,
    posted_by          UUID,
    trace_id           VARCHAR(64),
    idempotency_key    VARCHAR(120) NOT NULL,
    payload_hash       VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_material_returns_code UNIQUE (code),
    CONSTRAINT uk_material_returns_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_material_returns_status CHECK (status IN ('POSTED'))
);

CREATE INDEX idx_material_returns_work_order ON material_returns (work_order_id);
CREATE INDEX idx_material_returns_posted_at ON material_returns (posted_at DESC);

CREATE TABLE material_return_lines (
    material_return_line_id UUID PRIMARY KEY,
    material_return_id      UUID           NOT NULL REFERENCES material_returns (material_return_id),
    line_no                 INTEGER        NOT NULL,
    source_issue_line_id    UUID           NOT NULL REFERENCES material_issue_lines (issue_line_id),
    quantity                NUMERIC(19, 6) NOT NULL,
    stock_movement_id       UUID           NOT NULL REFERENCES stock_movements (movement_id),
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_material_return_lines_line_no UNIQUE (material_return_id, line_no),
    CONSTRAINT chk_material_return_lines_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_material_return_lines_source_issue_line ON material_return_lines (source_issue_line_id);

ALTER TABLE wip_transactions DROP CONSTRAINT chk_wip_transactions_type;
ALTER TABLE wip_transactions ADD CONSTRAINT chk_wip_transactions_type
    CHECK (transaction_type IN ('START', 'MATERIAL_ISSUED', 'MATERIAL_RETURNED', 'OUTPUT_COMPLETED',
                                'OUTPUT_RECEIPTED', 'SCRAP_REPORTED', 'REWORK_REPORTED'));

INSERT INTO permissions (code, resource, action, description)
VALUES
    ('PERM_WIP_READ', 'WIP', 'READ', 'Read the work-in-progress of work orders: material, operations, rework, output awaiting receipt'),
    ('PERM_MATERIAL_RETURN_READ', 'MATERIAL_RETURN', 'READ', 'Read material returns'),
    ('PERM_MATERIAL_RETURN_MANAGE', 'MATERIAL_RETURN', 'MANAGE', 'Return unused issued material to its source')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('PERM_WIP_READ', 'PERM_MATERIAL_RETURN_READ', 'PERM_MATERIAL_RETURN_MANAGE')
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
