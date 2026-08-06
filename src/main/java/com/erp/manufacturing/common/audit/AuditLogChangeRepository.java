package com.erp.manufacturing.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogChangeRepository extends JpaRepository<AuditLogChange, UUID> {

    /**
     * C2-1: backs the {@code changes[]} field on {@code GET /audit-logs/{id}}. Genuinely empty today
     * — nothing writes to {@code audit_log_changes} yet (đợt 2, not in scope here) — but the query is
     * real, not a hardcoded {@code []}: it starts returning real data the moment a future phase writes
     * to this table, with zero change needed on the read side.
     */
    List<AuditLogChange> findByAuditIdOrderByCreatedAtAsc(UUID auditId);
}
