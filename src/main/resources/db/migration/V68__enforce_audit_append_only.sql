-- V68 — Audit refactor AR-7: make the audit tables append-only in the database, not just in the ORM.
--
-- Until now "append-only" was an @Immutable annotation and a naming convention. Hibernate's
-- @Immutable stops *this* application from issuing an UPDATE; it does nothing about a psql session,
-- a migration script, a future repository method, or the very JpaRepository the audit code already
-- exposes (save/delete are inherited, public, and one autocomplete away). An audit trail that the
-- application can rewrite is not evidence of anything.
--
-- WHY A TRIGGER AND NOT ONLY ROLE GRANTS
-- Revoking UPDATE/DELETE from the runtime role is the right complementary control and is documented
-- at the bottom of this file, but on its own it is not testable here and not sufficient anywhere:
--   * a SUPERUSER bypasses grants entirely — including the role used by Testcontainers, which would
--     make any integration test asserting immutability pass for the wrong reason;
--   * the role name differs per environment, so a migration cannot name it portably.
-- A trigger applies to every role, superuser included, and is therefore the control that can actually
-- be proven by a test running as the real runtime user.

CREATE OR REPLACE FUNCTION audit_reject_mutation() RETURNS trigger AS $$
BEGIN
    -- Single, explicit maintenance escape hatch: archive/retention jobs set this GUC for the length
    -- of their own transaction (SET LOCAL audit.maintenance = 'on'). It is transaction-scoped by
    -- convention and invisible to ordinary application code, which never sets it.
    IF current_setting('audit.maintenance', true) = 'on' THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'audit tables are append-only: % on % is not permitted', TG_OP, TG_TABLE_NAME
        USING ERRCODE = '42501';  -- insufficient_privilege
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION audit_reject_mutation() IS
    'Rejects UPDATE/DELETE on immutable audit tables unless audit.maintenance is set for the transaction';

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_reject_mutation();

CREATE TRIGGER trg_audit_log_changes_append_only
    BEFORE UPDATE OR DELETE ON audit_log_changes
    FOR EACH ROW EXECUTE FUNCTION audit_reject_mutation();

CREATE TRIGGER trg_audit_log_entities_append_only
    BEFORE UPDATE OR DELETE ON audit_log_entities
    FOR EACH ROW EXECUTE FUNCTION audit_reject_mutation();

-- audit_outbox is deliberately NOT protected. It is a work queue: the dispatcher must be able to
-- move a row through PENDING → PROCESSING → PROCESSED and prune drained rows. Conflating the queue
-- with the ledger is what would force the ledger to be mutable.

-- ─────────────────────────────────────────────────────────────────────────────
-- Complementary control, applied per environment because the role name is not portable.
-- Run once as an administrator against production/staging, with the real runtime role:
--
--     REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs, audit_log_changes, audit_log_entities
--         FROM <runtime_role>;
--     GRANT SELECT, INSERT ON audit_logs, audit_log_changes, audit_log_entities TO <runtime_role>;
--
-- Defence in depth: grants stop the statement from being attempted, the trigger stops it from
-- succeeding even for a role that has the privilege. Neither is a substitute for the other, and the
-- trigger is the one an automated test can verify.
-- ─────────────────────────────────────────────────────────────────────────────
