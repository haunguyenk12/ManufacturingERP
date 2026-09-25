package com.erp.manufacturing.common.audit;


import java.util.List;
import java.util.UUID;

public interface AuditLogEntityRowRepository extends AppendOnlyRepository<AuditLogEntityRow, UUID> {

    List<AuditLogEntityRow> findByAuditIdOrderByRelationAscEntityTypeAsc(UUID auditId);

    /**
     * Backs the {@code relatedEntityType}/{@code relatedEntityId} filter on the read API: "show me
     * every event that touched this permission", which the legacy single-target columns can only
     * answer for events named after it.
     */
    List<AuditLogEntityRow> findByEntityTypeAndEntityId(String entityType, String entityId);
}
