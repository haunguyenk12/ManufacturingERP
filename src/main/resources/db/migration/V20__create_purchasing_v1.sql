-- V20__create_purchasing_v1.sql
-- Purchasing v1: suppliers, item suppliers, purchase requisitions, purchase orders, and goods receipts.

CREATE TABLE suppliers (
    supplier_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID         NOT NULL REFERENCES companies(company_id),
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    phone       VARCHAR(60),
    address     TEXT,
    tax_code    VARCHAR(100),
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       DEFAULT 0,
    CONSTRAINT uk_suppliers_company_code UNIQUE (company_id, code),
    CONSTRAINT chk_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE item_suppliers (
    item_supplier_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id               UUID          NOT NULL REFERENCES items(item_id),
    supplier_id           UUID          NOT NULL REFERENCES suppliers(supplier_id),
    supplier_item_code    VARCHAR(120),
    lead_time_days        INTEGER       NOT NULL DEFAULT 0,
    minimum_order_quantity NUMERIC(19,6),
    unit_price            NUMERIC(19,6),
    currency_code         VARCHAR(3),
    preferred             BOOLEAN       NOT NULL DEFAULT FALSE,
    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT        DEFAULT 0,
    CONSTRAINT uk_item_suppliers_item_supplier UNIQUE (item_id, supplier_id),
    CONSTRAINT chk_item_suppliers_lead_time CHECK (lead_time_days >= 0),
    CONSTRAINT chk_item_suppliers_moq CHECK (minimum_order_quantity IS NULL OR minimum_order_quantity > 0),
    CONSTRAINT chk_item_suppliers_unit_price CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT chk_item_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_item_suppliers_preferred_active
    ON item_suppliers(item_id)
    WHERE preferred = TRUE AND status = 'ACTIVE';

CREATE TABLE purchase_requisitions (
    purchase_requisition_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id              UUID         NOT NULL REFERENCES companies(company_id),
    plant_id                UUID         NOT NULL REFERENCES plants(plant_id),
    warehouse_id            UUID         NOT NULL REFERENCES warehouses(warehouse_id),
    requisition_no          VARCHAR(100) NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    needed_by_date          DATE         NOT NULL,
    source_type             VARCHAR(80),
    source_id               UUID,
    decision_note           TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT       DEFAULT 0,
    CONSTRAINT uk_purchase_requisitions_company_no UNIQUE (company_id, requisition_no),
    CONSTRAINT chk_purchase_requisitions_status CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'CONVERTED', 'CANCELLED'))
);

CREATE TABLE purchase_requisition_lines (
    purchase_requisition_line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_requisition_id      UUID          NOT NULL REFERENCES purchase_requisitions(purchase_requisition_id),
    item_id                      UUID          NOT NULL REFERENCES items(item_id),
    supplier_id                  UUID          REFERENCES suppliers(supplier_id),
    requested_quantity           NUMERIC(19,6) NOT NULL,
    approved_quantity            NUMERIC(19,6),
    needed_by_date               DATE          NOT NULL,
    note                         TEXT,
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                   UUID,
    updated_by                   UUID,
    version                      BIGINT        DEFAULT 0,
    CONSTRAINT chk_pr_lines_requested_quantity CHECK (requested_quantity > 0),
    CONSTRAINT chk_pr_lines_approved_quantity CHECK (approved_quantity IS NULL OR approved_quantity > 0)
);

CREATE TABLE purchase_orders (
    purchase_order_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id            UUID         NOT NULL REFERENCES companies(company_id),
    plant_id              UUID         NOT NULL REFERENCES plants(plant_id),
    warehouse_id          UUID         NOT NULL REFERENCES warehouses(warehouse_id),
    supplier_id           UUID         NOT NULL REFERENCES suppliers(supplier_id),
    purchase_order_no     VARCHAR(100) NOT NULL,
    status                VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    order_date            DATE         NOT NULL,
    expected_date         DATE         NOT NULL,
    source_requisition_id UUID         REFERENCES purchase_requisitions(purchase_requisition_id),
    note                  TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT       DEFAULT 0,
    CONSTRAINT uk_purchase_orders_company_no UNIQUE (company_id, purchase_order_no),
    CONSTRAINT chk_purchase_orders_status CHECK (status IN ('DRAFT', 'SENT', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'))
);

CREATE TABLE purchase_order_lines (
    purchase_order_line_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id            UUID          NOT NULL REFERENCES purchase_orders(purchase_order_id),
    purchase_requisition_line_id UUID          REFERENCES purchase_requisition_lines(purchase_requisition_line_id),
    item_id                      UUID          NOT NULL REFERENCES items(item_id),
    ordered_quantity             NUMERIC(19,6) NOT NULL,
    received_quantity            NUMERIC(19,6) NOT NULL DEFAULT 0,
    unit_price                   NUMERIC(19,6),
    currency_code                VARCHAR(3),
    expected_date                DATE          NOT NULL,
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                   UUID,
    updated_by                   UUID,
    version                      BIGINT        DEFAULT 0,
    CONSTRAINT chk_po_lines_ordered_quantity CHECK (ordered_quantity > 0),
    CONSTRAINT chk_po_lines_received_quantity CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity),
    CONSTRAINT chk_po_lines_unit_price CHECK (unit_price IS NULL OR unit_price >= 0)
);

CREATE TABLE goods_receipts (
    goods_receipt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID         NOT NULL REFERENCES purchase_orders(purchase_order_id),
    warehouse_id      UUID         NOT NULL REFERENCES warehouses(warehouse_id),
    receipt_no        VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'POSTED',
    posted_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    idempotency_key   VARCHAR(120) NOT NULL,
    note              TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       DEFAULT 0,
    CONSTRAINT uk_goods_receipts_po_idempotency UNIQUE (purchase_order_id, idempotency_key),
    CONSTRAINT chk_goods_receipts_status CHECK (status IN ('POSTED', 'CANCELLED'))
);

CREATE TABLE goods_receipt_lines (
    goods_receipt_line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goods_receipt_id      UUID          NOT NULL REFERENCES goods_receipts(goods_receipt_id),
    purchase_order_line_id UUID         NOT NULL REFERENCES purchase_order_lines(purchase_order_line_id),
    item_id               UUID          NOT NULL REFERENCES items(item_id),
    lot_id                UUID          REFERENCES inventory_lots(lot_id),
    lot_code              VARCHAR(120),
    received_quantity     NUMERIC(19,6) NOT NULL,
    stock_movement_id     UUID          NOT NULL REFERENCES stock_movements(movement_id),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT        DEFAULT 0,
    CONSTRAINT chk_gr_lines_received_quantity CHECK (received_quantity > 0)
);

CREATE INDEX idx_suppliers_company_status ON suppliers(company_id, status);
CREATE INDEX idx_item_suppliers_item_status_preferred ON item_suppliers(item_id, status, preferred);
CREATE INDEX idx_item_suppliers_supplier_id ON item_suppliers(supplier_id);
CREATE INDEX idx_purchase_requisitions_company_plant_status_created
    ON purchase_requisitions(company_id, plant_id, status, created_at DESC);
CREATE INDEX idx_pr_lines_requisition_id ON purchase_requisition_lines(purchase_requisition_id);
CREATE INDEX idx_pr_lines_item_id ON purchase_requisition_lines(item_id);
CREATE INDEX idx_pr_lines_supplier_id ON purchase_requisition_lines(supplier_id);
CREATE INDEX idx_purchase_orders_company_plant_supplier_status_created
    ON purchase_orders(company_id, plant_id, supplier_id, status, created_at DESC);
CREATE INDEX idx_purchase_orders_source_requisition_id ON purchase_orders(source_requisition_id);
CREATE INDEX idx_po_lines_purchase_order_id ON purchase_order_lines(purchase_order_id);
CREATE INDEX idx_po_lines_requisition_line_id ON purchase_order_lines(purchase_requisition_line_id);
CREATE INDEX idx_po_lines_item_id ON purchase_order_lines(item_id);
CREATE INDEX idx_goods_receipts_po_status_posted ON goods_receipts(purchase_order_id, status, posted_at DESC);
CREATE INDEX idx_gr_lines_receipt_id ON goods_receipt_lines(goods_receipt_id);
CREATE INDEX idx_gr_lines_po_line_id ON goods_receipt_lines(purchase_order_line_id);
CREATE INDEX idx_gr_lines_stock_movement_id ON goods_receipt_lines(stock_movement_id);
