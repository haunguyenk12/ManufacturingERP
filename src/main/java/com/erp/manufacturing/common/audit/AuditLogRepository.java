package com.erp.manufacturing.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends AppendOnlyRepository<AuditLog, UUID> {

    /**
     * C2-1: every filter is exact-match (no {@code search=}/partial-text param in the spec), so the
     * {@code concat}/{@code like} class of the "lower(bytea)" bug (`CLAUDE.md §0.24`) does not apply
     * here. A *different* instance of the same underlying problem does: a bare {@code :from IS NULL}
     * with no other context on that parameter's own placeholder fails Postgres's parse-time type
     * inference with {@code could not determine data type of parameter}, caught by
     * {@code AuditLogRepositoryIT} (rule T2) — {@code cast(:from as timestamp)} fixes it the same way
     * {@code cast(:search as string)} fixes the String case elsewhere in this codebase. Only the
     * two {@code Instant} filters need it; the UUID/String equality filters bind fine bare (their
     * {@code = column} occurrence gives Postgres enough context that {@code WorkOrderRepository}'s
     * {@code productItemId}/{@code status} filters, same bare shape, already prove works).
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorUserId IS NULL OR a.userId = :actorUserId)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:action IS NULL OR a.action = :action)
              AND (:plantId IS NULL OR a.plantId = :plantId)
              AND (:traceId IS NULL OR a.traceId = :traceId)
              AND (cast(:from as timestamp) IS NULL OR a.createdAt >= :from)
              AND (cast(:to as timestamp) IS NULL OR a.createdAt <= :to)
            """)
    Page<AuditLog> search(@Param("actorUserId") UUID actorUserId,
                          @Param("entityType") String entityType,
                          @Param("entityId") String entityId,
                          @Param("action") String action,
                          @Param("plantId") UUID plantId,
                          @Param("traceId") String traceId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);

    /**
     * Idempotency check for {@code AuditLogMaterializer}. Backed by a unique index, so this is the
     * cheap path and the index is the guarantee — a redelivery that races past this check still
     * cannot produce a second row.
     */
    boolean existsByEventId(UUID eventId);

    /**
     * AR-6 search: adds outcome / source / scope / related-entity filters on top of the C2-1 set.
     *
     * <p>{@code occurred_at} is coalesced with {@code created_at} throughout. Rows written before
     * {@code V67} have no {@code occurred_at}, so filtering or sorting on the new column alone would
     * make every historical row vanish from a time-ranged query — the read API would appear to have
     * lost years of trail the moment this shipped.
     *
     * <p>The related-entity filter is a subquery over {@code audit_log_entities} rather than a join:
     * a join multiplies the parent row once per matching target, and a page of 20 would silently
     * return fewer than 20 distinct events.
     *
     * <p>{@code cast(:from as timestamp)} is not decoration — a parameter appearing only in an
     * {@code IS NULL} test gives Postgres nothing to infer a type from and fails at parse time with
     * {@code could not determine data type of parameter}. Same class of bug as {@code lower(bytea)}
     * elsewhere in this codebase; see {@code AuditLogRepositoryIT}.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorUserId IS NULL OR a.userId = :actorUserId)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:action IS NULL OR a.action = :action)
              AND (:outcome IS NULL OR a.status = :outcome)
              AND (:source IS NULL OR a.source = :source)
              AND (:companyId IS NULL OR a.companyId = :companyId)
              AND (:plantId IS NULL OR a.plantId = :plantId)
              AND (:warehouseId IS NULL OR a.warehouseId = :warehouseId)
              AND (:traceId IS NULL OR a.traceId = :traceId)
              AND (cast(:from as timestamp) IS NULL OR COALESCE(a.occurredAt, a.createdAt) >= :from)
              AND (cast(:to as timestamp) IS NULL OR COALESCE(a.occurredAt, a.createdAt) <= :to)
              AND (:relatedEntityType IS NULL OR EXISTS (
                    SELECT 1 FROM AuditLogEntityRow r
                    WHERE r.auditId = a.auditId
                      AND r.entityType = :relatedEntityType
                      AND (:relatedEntityId IS NULL OR r.entityId = :relatedEntityId)))
            """)
    Page<AuditLog> searchAdvanced(@Param("actorUserId") UUID actorUserId,
                                  @Param("entityType") String entityType,
                                  @Param("entityId") String entityId,
                                  @Param("action") String action,
                                  @Param("outcome") String outcome,
                                  @Param("source") String source,
                                  @Param("companyId") UUID companyId,
                                  @Param("plantId") UUID plantId,
                                  @Param("warehouseId") UUID warehouseId,
                                  @Param("traceId") String traceId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  @Param("relatedEntityType") String relatedEntityType,
                                  @Param("relatedEntityId") String relatedEntityId,
                                  Pageable pageable);

    List<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.createdAt BETWEEN :from AND :to ORDER BY a.createdAt DESC")
    List<AuditLog> findByUserIdAndTimeRange(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId);

    List<AuditLog> findByTraceId(String traceId);
}
