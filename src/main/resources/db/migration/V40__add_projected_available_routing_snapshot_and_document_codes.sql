-- V40 (F10): the three field-level debts F, G and H of the frontend spec
-- (FRONTEND_ALIGNMENT_ROADMAP.md §7.1). Everything here is additive; no wire contract changes.
--
-- 1. mrp_requirement_lines.projected_available_quantity  (debt G, spec §2.1 / §2.4)
--
--    MrpCalculationService has always computed this number and then thrown it away:
--        remainingCoverage = max(0, availableQuantity + openSupplyQuantity - consumedCoverage)
--    consumedCoverage is the coverage already eaten by *earlier* requirement lines for the same
--    (item, warehouse). It is never persisted, so a reader cannot recompute the value: deriving
--    availableQuantity + openSupplyQuantity in a mapper reports a number that is too large the
--    moment one item appears on more than one requirement line, and it breaks the identity the
--    frontend checks, netRequired = max(0, gross + safetyStock - projectedAvailable).
--
--    NULLABLE ON PURPOSE, and deliberately NOT backfilled. NULL means "run executed before V40",
--    not "nothing available" — the same convention V38 established for work_orders.cancel_reason.
--    Backfilling available + openSupply would write exactly the wrong number this column exists to
--    avoid, permanently, for every historical run.
--
-- 2. supply_suggestions.source_routing_code / _version  (debt F, spec §2.4 "Proposal")
--
--    A frozen snapshot of the routing that was ACTIVE when the run executed — the same contract as
--    work_orders.source_routing_code (invariant B49), which is why the types match V30 exactly
--    (VARCHAR(100) / VARCHAR(40)) rather than being re-guessed here. Note routing_version is a
--    STRING, not a number.
--    NULL is meaningful: BUY proposals have no routing at all, and a MAKE proposal whose item has
--    no ACTIVE routing is precisely the BLOCKED / MISSING_ROUTING case (B58, B59).
--
-- 3. material_issues.code, production_executions.code  (debt H, spec §4.2 / §5.2 "History")
--
--    Neither document had any human-facing number; production_receipts has had one since F5.
--    Same three-step shape as V36 did for mrp_runs: add nullable, backfill with the SAME formula
--    the entity's @PrePersist uses, then make it NOT NULL and unique. If the two formulas ever
--    drift, a historical document gets a code that cannot be traced back to its id — and UNIQUE
--    will not catch it. FlywayMigrationIT pins the two against each other.
--
--    Prefixes MI- and PE-. Spec §5.2 labels the execution number "Mã WIP", but wip_transactions is
--    a different ledger table with its own rows and no code column, so a WIP- prefix here would
--    suggest this identifies a wip_transactions row. Deliberate naming divergence.

ALTER TABLE mrp_requirement_lines
    ADD COLUMN projected_available_quantity NUMERIC(19,6);

ALTER TABLE mrp_requirement_lines
    ADD CONSTRAINT chk_mrp_req_projected_available
        CHECK (projected_available_quantity IS NULL OR projected_available_quantity >= 0);

ALTER TABLE supply_suggestions
    ADD COLUMN source_routing_code    VARCHAR(100),
    ADD COLUMN source_routing_version VARCHAR(40);

ALTER TABLE material_issues ADD COLUMN code VARCHAR(40);

-- Must stay identical to MaterialIssue.assignCode().
UPDATE material_issues
SET code = 'MI-' || upper(substring(issue_id::text, 1, 8))
WHERE code IS NULL;

ALTER TABLE material_issues ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX uk_material_issues_code ON material_issues (code);

ALTER TABLE production_executions ADD COLUMN code VARCHAR(40);

-- Must stay identical to ProductionExecution.assignCode().
UPDATE production_executions
SET code = 'PE-' || upper(substring(production_execution_id::text, 1, 8))
WHERE code IS NULL;

ALTER TABLE production_executions ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX uk_production_executions_code ON production_executions (code);
