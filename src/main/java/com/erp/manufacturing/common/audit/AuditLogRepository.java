package com.erp.manufacturing.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

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
