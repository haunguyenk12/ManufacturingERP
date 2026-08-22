-- Contract hardening for BE_SYSTEM_ISSUES_RESOLUTION_PLAN_2026-08-19.

-- DEC-01 / ISS-01, ISS-02: contains-search indexes.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_sales_orders_order_no_trgm
    ON sales_orders USING gin (lower(order_no) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_sales_orders_customer_name_trgm
    ON sales_orders USING gin (lower(customer_name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_items_code_trgm
    ON items USING gin (lower(code) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_items_name_trgm
    ON items USING gin (lower(name) gin_trgm_ops);

-- DEC-03 / ISS-06: explicit default roles used by deterministic MRP warehouse resolution.
ALTER TABLE item_warehouse_settings
    ADD COLUMN is_default_supply BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_default_output BOOLEAN NOT NULL DEFAULT FALSE;

CREATE OR REPLACE FUNCTION enforce_item_warehouse_default_role()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'ACTIVE' AND (NEW.is_default_supply OR NEW.is_default_output) AND EXISTS (
        SELECT 1
        FROM item_warehouse_settings s
        JOIN warehouses existing_w ON existing_w.warehouse_id = s.warehouse_id
        JOIN warehouses new_w ON new_w.warehouse_id = NEW.warehouse_id
        WHERE s.item_id = NEW.item_id
          AND s.setting_id IS DISTINCT FROM NEW.setting_id
          AND s.status = 'ACTIVE'
          AND existing_w.plant_id = new_w.plant_id
          AND ((NEW.is_default_supply AND s.is_default_supply)
               OR (NEW.is_default_output AND s.is_default_output))
    ) THEN
        RAISE EXCEPTION 'duplicate default item warehouse role for item %', NEW.item_id
            USING ERRCODE = '23505';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_item_warehouse_default_role
BEFORE INSERT OR UPDATE OF item_id, warehouse_id, status, is_default_supply, is_default_output
ON item_warehouse_settings
FOR EACH ROW EXECUTE FUNCTION enforce_item_warehouse_default_role();

ALTER TABLE mrp_requirement_lines
    ADD COLUMN warehouse_resolution_source VARCHAR(40) NOT NULL DEFAULT 'UNRESOLVED';
ALTER TABLE mrp_requirement_lines
    ADD CONSTRAINT chk_mrp_req_warehouse_resolution_source CHECK (warehouse_resolution_source IN (
        'DEMAND_WAREHOUSE','RUN_DEMAND_WAREHOUSE','SINGLE_ACTIVE_WAREHOUSE',
        'ITEM_WAREHOUSE_DEFAULT','ITEM_WAREHOUSE_ONLY','WAREHOUSE_TYPE_FALLBACK','UNRESOLVED'
    ));

ALTER TABLE supply_suggestions
    ADD COLUMN output_warehouse_id UUID REFERENCES warehouses(warehouse_id),
    ADD COLUMN receiving_warehouse_id UUID REFERENCES warehouses(warehouse_id);
UPDATE supply_suggestions
SET output_warehouse_id = warehouse_id
WHERE suggestion_type = 'WORK_ORDER' AND output_warehouse_id IS NULL;
UPDATE supply_suggestions
SET receiving_warehouse_id = warehouse_id
WHERE suggestion_type = 'PURCHASE_REQUISITION' AND receiving_warehouse_id IS NULL;
CREATE INDEX idx_supply_suggestions_output_warehouse ON supply_suggestions(output_warehouse_id);
CREATE INDEX idx_supply_suggestions_receiving_warehouse ON supply_suggestions(receiving_warehouse_id);

-- DEC-09 / ISS-04, ISS-12: Over-BOM requests persist first and post only after manager approval.
ALTER TABLE material_issues DROP CONSTRAINT chk_material_issues_status;
ALTER TABLE material_issues ADD CONSTRAINT chk_material_issues_status
    CHECK (status IN ('PENDING_APPROVAL','POSTED','REJECTED','CANCELLED'));
ALTER TABLE material_issues
    ALTER COLUMN posted_at DROP NOT NULL,
    ADD COLUMN requested_at TIMESTAMPTZ,
    ADD COLUMN decided_at TIMESTAMPTZ,
    ADD COLUMN decided_by UUID,
    ADD COLUMN rejection_reason TEXT;
UPDATE material_issues SET requested_at = COALESCE(posted_at, created_at) WHERE requested_at IS NULL;
ALTER TABLE material_issues ALTER COLUMN requested_at SET NOT NULL;

ALTER TABLE material_issue_lines
    ALTER COLUMN stock_movement_id DROP NOT NULL,
    ADD COLUMN requested_lot_code VARCHAR(120),
    ADD COLUMN requested_lot_id UUID,
    ADD COLUMN requested_serial_id UUID,
    ADD COLUMN issue_reason VARCHAR(1000),
    ADD COLUMN reason_code VARCHAR(30),
    ADD COLUMN source_execution_id UUID REFERENCES production_executions(production_execution_id);
ALTER TABLE material_issue_lines ADD CONSTRAINT chk_material_issue_reason_code
    CHECK (reason_code IS NULL OR reason_code IN ('REWORK','PROCESS_LOSS','DAMAGE','SETUP_LOSS','OTHER'));
CREATE INDEX idx_material_issue_lines_source_execution ON material_issue_lines(source_execution_id);

INSERT INTO permissions (code, resource, action, description)
VALUES ('PERM_MATERIAL_ISSUE_APPROVE', 'MATERIAL_ISSUE', 'APPROVE',
        'Approve or reject pending Over-BOM material requests')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'PERM_MATERIAL_ISSUE_APPROVE'
WHERE r.code IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

-- DEC-05 / ISS-08: normalization is trim-only and case-sensitive. Existing item+code uniqueness
-- remains the authoritative database race barrier.
DO $$
BEGIN
    IF EXISTS (
        SELECT item_id, btrim(lot_code)
        FROM inventory_lots
        GROUP BY item_id, btrim(lot_code)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'inventory lot codes conflict after trim normalization';
    END IF;
END $$;
UPDATE inventory_lots SET lot_code = btrim(lot_code) WHERE lot_code <> btrim(lot_code);
ALTER TABLE inventory_lots ADD CONSTRAINT chk_inventory_lots_code_trimmed
    CHECK (lot_code = btrim(lot_code) AND length(lot_code) > 0);
