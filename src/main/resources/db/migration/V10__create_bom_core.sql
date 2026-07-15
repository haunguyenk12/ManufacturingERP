-- V10__create_bom_core.sql
-- Multi-level BOM foundation with revisioning and draft/active lifecycle.

CREATE TABLE bom_headers (
    bom_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id     UUID         NOT NULL REFERENCES companies(company_id),
    parent_item_id UUID         NOT NULL REFERENCES items(item_id),
    revision       VARCHAR(40)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    description    TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT       DEFAULT 0,
    CONSTRAINT uk_bom_headers_company_parent_revision UNIQUE (company_id, parent_item_id, revision),
    CONSTRAINT chk_bom_headers_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_bom_headers_active_parent
    ON bom_headers(company_id, parent_item_id)
    WHERE status = 'ACTIVE';

CREATE TABLE bom_lines (
    line_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bom_id            UUID          NOT NULL REFERENCES bom_headers(bom_id) ON DELETE CASCADE,
    component_item_id UUID          NOT NULL REFERENCES items(item_id),
    line_no           INTEGER       NOT NULL,
    quantity_per      NUMERIC(19,6) NOT NULL,
    scrap_rate        NUMERIC(9,6)  NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT        DEFAULT 0,
    CONSTRAINT uk_bom_lines_bom_component UNIQUE (bom_id, component_item_id),
    CONSTRAINT uk_bom_lines_bom_line_no UNIQUE (bom_id, line_no),
    CONSTRAINT chk_bom_lines_quantity CHECK (quantity_per > 0),
    CONSTRAINT chk_bom_lines_scrap_rate CHECK (scrap_rate >= 0 AND scrap_rate < 1)
);

CREATE INDEX idx_bom_headers_company_id ON bom_headers(company_id);
CREATE INDEX idx_bom_headers_parent_item_id ON bom_headers(parent_item_id);
CREATE INDEX idx_bom_headers_status ON bom_headers(status);
CREATE INDEX idx_bom_lines_bom_id ON bom_lines(bom_id);
CREATE INDEX idx_bom_lines_component_item_id ON bom_lines(component_item_id);
