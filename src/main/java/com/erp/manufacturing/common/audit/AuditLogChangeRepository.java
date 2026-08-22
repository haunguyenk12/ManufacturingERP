package com.erp.manufacturing.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogChangeRepository extends JpaRepository<AuditLogChange, UUID> {

    /** Backs the {@code changes[]} field on {@code GET /audit-logs/{id}}. */
    List<AuditLogChange> findByAuditIdOrderByCreatedAtAsc(UUID auditId);
}
