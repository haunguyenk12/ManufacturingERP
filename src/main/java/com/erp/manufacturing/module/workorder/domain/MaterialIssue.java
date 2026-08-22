package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "material_issues", indexes = {
        @Index(name = "idx_material_issues_work_order_id", columnList = "work_order_id"),
        @Index(name = "idx_material_issues_status", columnList = "status"),
        @Index(name = "idx_material_issues_posted_at", columnList = "posted_at DESC")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialIssue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "issue_id", updatable = false, nullable = false)
    private UUID issueId;

    /** Human-facing document number (spec §4.2 "History"), {@code MI-} + 8 hex of the id. */
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MaterialIssueStatus status = MaterialIssueStatus.POSTED;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    /** SHA-256 of the request payload; null for documents written before V25. */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * Business traceability, persisted on the document (spec §4). Not the {@code X-Trace-Id}
     * response header — see {@code .claude/rules/error-handling.md} §5.1.
     */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaterialIssueLine> lines = new ArrayList<>();

    /**
     * Derives the document number from the row's own id.
     *
     * <p>Must run as {@code @PrePersist} and not after {@code save()}: Hibernate snapshots the
     * entity when it queues the insert, so a field set afterwards is simply absent from the INSERT
     * and the NOT NULL column blows up. {@code GenerationType.UUID} is a before-execution generator,
     * so {@code issueId} is already assigned by the time this callback fires. Same lesson as
     * {@code MrpRun.assignCode()} (CLAUDE.md §0.12 #5).
     *
     * <p>Must stay the same formula as the {@code V40} backfill
     * ({@code 'MI-' || upper(substring(issue_id::text, 1, 8))}).
     */
    @PrePersist
    public void assignCode() {
        if (code == null) {
            code = "MI-" + issueId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
    }

    public boolean isPendingApproval() {
        return status == MaterialIssueStatus.PENDING_APPROVAL;
    }

    public void markPosted(Instant now, UUID actor) {
        status = MaterialIssueStatus.POSTED;
        postedAt = now;
        decidedAt = now;
        decidedBy = actor;
    }

    public void reject(Instant now, UUID actor, String reason) {
        status = MaterialIssueStatus.REJECTED;
        decidedAt = now;
        decidedBy = actor;
        rejectionReason = reason;
    }
}
