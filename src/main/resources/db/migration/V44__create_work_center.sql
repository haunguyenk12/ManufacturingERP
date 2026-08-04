-- V44__create_work_center.sql
-- C2-6 Part A: Work Center master data (BACKEND_CAPSTONE2_API_GAPS.md §3.4).
-- Per-plant, not company-level (see NEXT_PHASE_PLAN.md C2-6 §1.1): matches Warehouse/actual
-- Routing usage and lets the future Capacity Board (C2-8) filter by plant through Work Center
-- itself, instead of an indirection through Work Order.
--
-- C2-6 Part B: routing_operations.work_center_id — nullable FK replacing the free-text
-- work_center_code. NOT backfilled: old work_center_code values are free text with no plant
-- attached (routing is company-level), so guessing a plant for historical rows would write wrong
-- data permanently. See §1.2/§Part B "Thiết kế đã chốt" #1 for the full reasoning. work_center_code
-- stays in place (unmapped from JPA) so historical rows remain readable.

CREATE TABLE work_centers (
    work_center_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id           UUID          NOT NULL REFERENCES plants(plant_id),
    code                VARCHAR(100)  NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    description         TEXT,
    capacity_unit_type  VARCHAR(20)   NOT NULL,
    capacity_units      INTEGER       NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT        DEFAULT 0,
    CONSTRAINT uk_work_centers_plant_code UNIQUE (plant_id, code),
    CONSTRAINT chk_work_centers_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_work_centers_capacity_unit_type CHECK (capacity_unit_type IN ('MACHINE', 'LINE', 'LABOR_TEAM')),
    CONSTRAINT chk_work_centers_capacity_units CHECK (capacity_units > 0)
);

CREATE INDEX idx_work_centers_plant_id ON work_centers(plant_id);
CREATE INDEX idx_work_centers_status ON work_centers(status);

ALTER TABLE routing_operations
    ADD COLUMN work_center_id UUID REFERENCES work_centers(work_center_id);

-- work_center_code was NOT NULL (V30) because it used to be the only reference. New rows are no
-- longer written through it (JPA doesn't map the column anymore), so it must accept NULL going
-- forward; existing rows keep their historical text untouched.
ALTER TABLE routing_operations
    ALTER COLUMN work_center_code DROP NOT NULL;

CREATE INDEX idx_routing_operations_work_center_id ON routing_operations(work_center_id);
