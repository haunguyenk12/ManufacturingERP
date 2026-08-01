-- V35__create_work_order_demand_allocation.sql
-- Phase F6 – fulfillment allocation (spec §2.4 "Demand allocation", §7.1).
--
-- Closes the last open link of the end-to-end scenario: a work order created from a MAKE proposal
-- now records WHICH sales order line it was raised for, and QC releasing the output lot to
-- AVAILABLE is what turns that allocation into fulfilled quantity on the sales order.
--
-- Deliberately NOT stored as an association from the sales side: the row is part of the work order
-- aggregate (it lives and dies with the work order), while sales_order_lines only ever learns the
-- aggregate result via fulfilled_quantity.

CREATE TABLE work_order_demand_allocations (
    allocation_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id       UUID          NOT NULL REFERENCES work_orders(work_order_id) ON DELETE CASCADE,
    sales_order_line_id UUID          NOT NULL REFERENCES sales_order_lines(sales_order_line_id),
    allocated_quantity  NUMERIC(19,6) NOT NULL,
    fulfilled_quantity  NUMERIC(19,6) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT        DEFAULT 0,
    -- One row per (work order, sales order line). Without this a second convert of the same
    -- proposal could double-count the same demand, and the fulfilment loop keys its per-line
    -- increments by sales_order_line_id.
    CONSTRAINT uk_wo_demand_allocations UNIQUE (work_order_id, sales_order_line_id),
    CONSTRAINT chk_wo_demand_allocations_allocated CHECK (allocated_quantity > 0),
    -- Last-resort ceiling for invariant B63, mirroring chk_sales_order_lines_fulfilled in V28.
    CONSTRAINT chk_wo_demand_allocations_fulfilled CHECK (
        fulfilled_quantity >= 0 AND fulfilled_quantity <= allocated_quantity
    )
);

-- Read path 1: QC disposition and the work-order response both fetch every allocation of a
-- work order (or of a whole page of work orders) in one query — rule C14.
CREATE INDEX idx_wo_demand_allocations_work_order ON work_order_demand_allocations(work_order_id);
-- Read path 2: resolving which sales order lines a batch of allocations points at.
CREATE INDEX idx_wo_demand_allocations_sales_order_line ON work_order_demand_allocations(sales_order_line_id);
