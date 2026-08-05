-- V50__create_costing.sql
-- P3: Costing Engine (MANUFACTURING_GAP_ROADMAP.md — "largest theoretical gap": no cost data
-- anywhere in the schema before this migration).
--
-- item_standard_costs: one row per item (upsert, no history/effectiveDate versioning — nothing in
-- the system needs "cost as of a past date" yet, adding it now would be speculative). Company-scoped
-- like bom_headers/routing_headers, not per-plant (cost is a property of the item within a company)
-- and not global like uoms. material_cost is only meaningful for items WITHOUT an ACTIVE BOM (leaf/
-- purchased items) — for a manufactured item, material cost is derived from the BOM roll-up instead
-- and this column is simply unused; no schema-level distinction needed, CostingService picks the
-- right source based on whether an ACTIVE BOM exists.
--
-- work_order_cost_accumulators: one row per work order, accumulated as material is issued
-- (MaterialIssueService) and as production is reported (ProductionExecutionService). No history —
-- same "running total" shape as work_orders.actual_good_quantity etc, not a ledger.

CREATE TABLE item_standard_costs (
    item_standard_cost_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID          NOT NULL REFERENCES companies(company_id),
    item_id         UUID          NOT NULL REFERENCES items(item_id),
    material_cost   NUMERIC(19,6) NOT NULL DEFAULT 0,
    labor_cost      NUMERIC(19,6) NOT NULL DEFAULT 0,
    overhead_cost   NUMERIC(19,6) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT        DEFAULT 0,
    CONSTRAINT uk_item_standard_costs_item UNIQUE (item_id),
    CONSTRAINT chk_item_standard_costs_material_cost CHECK (material_cost >= 0),
    CONSTRAINT chk_item_standard_costs_labor_cost CHECK (labor_cost >= 0),
    CONSTRAINT chk_item_standard_costs_overhead_cost CHECK (overhead_cost >= 0)
);

CREATE INDEX idx_item_standard_costs_company_id ON item_standard_costs(company_id);

CREATE TABLE work_order_cost_accumulators (
    work_order_cost_accumulator_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id               UUID          NOT NULL REFERENCES work_orders(work_order_id),
    material_cost_accumulated   NUMERIC(19,6) NOT NULL DEFAULT 0,
    labor_cost_accumulated      NUMERIC(19,6) NOT NULL DEFAULT 0,
    overhead_cost_accumulated   NUMERIC(19,6) NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID,
    version                     BIGINT        DEFAULT 0,
    CONSTRAINT uk_work_order_cost_accumulators_work_order UNIQUE (work_order_id),
    CONSTRAINT chk_work_order_cost_accumulators_material CHECK (material_cost_accumulated >= 0),
    CONSTRAINT chk_work_order_cost_accumulators_labor CHECK (labor_cost_accumulated >= 0),
    CONSTRAINT chk_work_order_cost_accumulators_overhead CHECK (overhead_cost_accumulated >= 0)
);
