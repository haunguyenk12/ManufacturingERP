-- C2-1: Audit Logs read API needs a plantId filter (BACKEND_CAPSTONE2_API_GAPS.md §3.3).
--
-- Nullable, no backfill: audit_logs is append-only, so every historical row stays NULL.
-- Scope decision for C2-1 (confirmed with user): this migration only adds the column. Wiring
-- real-time population from every audited call site (RequestContext, AuditLogEvent,
-- AuditableAspect, every direct auditLogService.log*() call) is a separate, larger phase — new
-- rows also stay NULL until that phase ships. Filtering by plantId is correctly a no-op today.
ALTER TABLE audit_logs ADD COLUMN plant_id UUID REFERENCES plants(plant_id);

CREATE INDEX idx_audit_plant_id ON audit_logs(plant_id);
