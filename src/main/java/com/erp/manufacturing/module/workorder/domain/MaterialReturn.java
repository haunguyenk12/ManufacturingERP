package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Unused issued material going back to the source it was issued from (G8d, FE R16, D3). Written POSTED in one
 * transaction with its stock movements and never edited afterwards (FE Q36).
 */
@Entity
@Table(name = "material_returns", indexes = {
        @Index(name = "idx_material_returns_work_order", columnList = "work_order_id"),
        @Index(name = "idx_material_returns_posted_at", columnList = "posted_at DESC")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialReturn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "material_return_id", updatable = false, nullable = false)
    private UUID materialReturnId;

    /** {@code MR-} + 8 hex of the id, like {@code MI-} on material issues. */
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MaterialReturnStatus status = MaterialReturnStatus.POSTED;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "posted_by")
    private UUID postedBy;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @OneToMany(mappedBy = "materialReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<MaterialReturnLine> lines = new ArrayList<>();

    /** Same reason as {@code MaterialIssue.assignCode()}: the id exists before the insert, a field set later does not. */
    @PrePersist
    public void assignCode() {
        if (code == null) {
            code = "MR-" + materialReturnId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
    }
}
