package com.erp.manufacturing.common.audit;


import java.util.List;
import java.util.UUID;

public interface AuditLogChangeRepository extends AppendOnlyRepository<AuditLogChange, UUID> {

    /** Backs the {@code changes[]} field on {@code GET /audit-logs/{id}}. */
    List<AuditLogChange> findByAuditIdOrderByCreatedAtAsc(UUID auditId);
}
