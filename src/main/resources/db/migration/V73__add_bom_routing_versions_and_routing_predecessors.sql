-- V73__add_bom_routing_versions_and_routing_predecessors.sql
-- G5 (FE_HANDOFF_GD_05): business version numbers, output quantity and effective dates on BOM and
-- routing headers, plus routing operation predecessors (debt B / C2-8b).
--
-- quantity_per stays the source of truth of a BOM line (NEXT_PHASE_PLAN.md §12.2 D1): MRP, the work
-- order snapshot and the costing roll-up read it unchanged. output_quantity only lets the batch-size
-- form be stored and shown again; componentQuantity = quantity_per × output_quantity is computed at
-- read time. Every existing BOM gets output_quantity = 1, which reproduces exactly what it meant.

-- ── BOM ─────────────────────────────────────────────────────────────────────

ALTER TABLE bom_headers
    ADD COLUMN bom_code        VARCHAR(160),
    ADD COLUMN version_number  INTEGER,
    ADD COLUMN output_quantity NUMERIC(19,6) NOT NULL DEFAULT 1,
    ADD COLUMN effective_from  TIMESTAMPTZ;

-- version_number: 1..n per (company, product) family in creation order. revision is NOT parsed —
-- it is a free-text label and stays one (FE Q3).
WITH numbered AS (
    SELECT bom_id,
           ROW_NUMBER() OVER (PARTITION BY company_id, parent_item_id ORDER BY created_at, bom_id) AS n
    FROM bom_headers
)
UPDATE bom_headers b
SET version_number = numbered.n
FROM numbered
WHERE numbered.bom_id = b.bom_id;

-- bom_code: product code + revision, the same rule BomService uses for a BOM created through the old
-- header endpoint. Wider than the 100 characters a request may send, because item code (100) plus a
-- revision (40) can exceed it and history must not be truncated into a collision.
UPDATE bom_headers b
SET bom_code = UPPER(i.code || '-' || b.revision)
FROM items i
WHERE i.item_id = b.parent_item_id;

-- "A-B" + "C" and "A" + "B-C" spell the same code. Rare, but the unique index below would refuse the
-- migration, so the later row of a collision gets its version number appended.
WITH collisions AS (
    SELECT bom_id,
           ROW_NUMBER() OVER (PARTITION BY company_id, UPPER(bom_code) ORDER BY created_at, bom_id) AS rn
    FROM bom_headers
)
UPDATE bom_headers b
SET bom_code = b.bom_code || '-V' || b.version_number
FROM collisions
WHERE collisions.bom_id = b.bom_id
  AND collisions.rn > 1;

ALTER TABLE bom_headers
    ALTER COLUMN bom_code SET NOT NULL,
    ALTER COLUMN version_number SET NOT NULL,
    ADD CONSTRAINT chk_bom_headers_output_quantity CHECK (output_quantity > 0),
    ADD CONSTRAINT chk_bom_headers_version_number CHECK (version_number > 0),
    ADD CONSTRAINT uk_bom_headers_company_parent_version_number
        UNIQUE (company_id, parent_item_id, version_number);

-- Case-insensitive per company (FE §3.4). The service always stores the upper-cased form; the index
-- is what holds when two requests race past the existence check.
CREATE UNIQUE INDEX uk_bom_headers_company_bom_code ON bom_headers (company_id, UPPER(bom_code));

-- ── Routing ─────────────────────────────────────────────────────────────────

ALTER TABLE routings
    ADD COLUMN version_number INTEGER,
    ADD COLUMN name           VARCHAR(255),
    ADD COLUMN effective_from TIMESTAMPTZ;

WITH numbered AS (
    SELECT routing_id,
           ROW_NUMBER() OVER (PARTITION BY company_id, item_id ORDER BY created_at, routing_id) AS n
    FROM routings
)
UPDATE routings r
SET version_number = numbered.n
FROM numbered
WHERE numbered.routing_id = r.routing_id;

ALTER TABLE routings
    ALTER COLUMN version_number SET NOT NULL,
    ADD CONSTRAINT chk_routings_version_number CHECK (version_number > 0),
    ADD CONSTRAINT uk_routings_company_item_version_number UNIQUE (company_id, item_id, version_number);

-- An operation may wait for any earlier operation of the same routing. "Earlier" means a smaller
-- sequence, which the service enforces and which by itself rules out cycles. Rows go with either
-- operation when a draft is re-saved without it.
CREATE TABLE routing_operation_predecessors (
    routing_operation_id     UUID NOT NULL REFERENCES routing_operations (routing_operation_id) ON DELETE CASCADE,
    predecessor_operation_id UUID NOT NULL REFERENCES routing_operations (routing_operation_id) ON DELETE CASCADE,
    CONSTRAINT pk_routing_operation_predecessors PRIMARY KEY (routing_operation_id, predecessor_operation_id),
    CONSTRAINT chk_routing_operation_predecessors_not_self CHECK (routing_operation_id <> predecessor_operation_id)
);

CREATE INDEX idx_routing_operation_predecessors_predecessor
    ON routing_operation_predecessors (predecessor_operation_id);
