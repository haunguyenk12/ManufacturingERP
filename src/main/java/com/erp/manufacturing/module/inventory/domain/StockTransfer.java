package com.erp.manufacturing.module.inventory.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Warehouse-to-warehouse transfer inside one plant (G4-2). Always posted: G4 has no draft, approval,
 * edit, cancel or in-transit state, so there is no status column — the wire reports a constant
 * {@code POSTED}, the same choice F10 made for production executions.
 */
@Entity
@Table(name = "stock_transfers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransfer extends BaseEntity {

    public static final String STATUS_POSTED = "POSTED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transfer_id", updatable = false, nullable = false)
    private UUID transferId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_warehouse_id", nullable = false)
    private Warehouse fromWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_warehouse_id", nullable = false)
    private Warehouse toWarehouse;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    /** Business traceability, not the {@code X-Trace-Id} header (error-handling.md §5.1). */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<StockTransferLine> lines = new ArrayList<>();

    /**
     * {@code @PrePersist}, not after {@code save()}: Hibernate snapshots the INSERT when it queues it,
     * so a code assigned later never reaches the NOT NULL column (B68). The UUID generator runs before
     * this callback, so the id is already there.
     */
    @PrePersist
    public void assignCode() {
        if (code == null) {
            code = "TR-" + transferId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
    }

    public void addLine(StockTransferLine line) {
        line.setTransfer(this);
        lines.add(line);
    }
}
