-- V42__create_uom.sql
-- C2-3: Unit of Measure master data (BACKEND_CAPSTONE2_API_GAPS.md §3.1).
--
-- Global, NOT company-scoped: the FE gap doc's GET /uoms carries no companyId/plantId in its query,
-- unlike every other master-data table in this schema (items, suppliers, boms all have company_id).
-- "kg", "pcs", "L" are universal concepts, not owned by a company. See NEXT_PHASE_PLAN.md (C2-3)
-- "Thiết kế đã chốt" #1 for the full reasoning.

CREATE TABLE uoms (
    uom_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       DEFAULT 0,
    CONSTRAINT uk_uoms_code UNIQUE (code),
    CONSTRAINT chk_uoms_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
