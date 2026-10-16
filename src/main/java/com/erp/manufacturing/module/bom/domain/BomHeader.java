package com.erp.manufacturing.module.bom.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bom_headers", indexes = {
        @Index(name = "idx_bom_headers_company_id", columnList = "company_id"),
        @Index(name = "idx_bom_headers_parent_item_id", columnList = "parent_item_id"),
        @Index(name = "idx_bom_headers_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BomHeader extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bom_id", updatable = false, nullable = false)
    private UUID bomId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_item_id", nullable = false)
    private Item parentItem;

    @Column(name = "revision", nullable = false, length = 40)
    private String revision;

    /**
     * Business code of this version (G5). Unique per company, case-insensitive, one per version —
     * a copy gets its own code. Wider than the 100 characters a request may send only so the V73
     * backfill (product code + revision) never had to truncate history.
     */
    @Column(name = "bom_code", nullable = false, length = 160)
    private String bomCode;

    /** Server-assigned, 1..n per company + product in creation order (G5, FE Q3). Never parsed from {@link #revision}. */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * Batch size the lines are entered for (G5, D1). Lines still store {@code quantityPer} per ONE
     * unit of output — that is what MRP, the work order snapshot and costing read — so this only
     * changes how the form is stored and shown: componentQuantity = quantityPer × outputQuantity.
     */
    @Column(name = "output_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal outputQuantity = BigDecimal.ONE;

    /** {@code null}: no delay, activatable now. A later instant blocks activation until then (G5, D3). */
    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BomStatus status = BomStatus.DRAFT;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<BomLine> lines = new ArrayList<>();

    public boolean isDraft() {
        return status == BomStatus.DRAFT;
    }

    public void activate() {
        status = BomStatus.ACTIVE;
    }

    public void deactivate() {
        status = BomStatus.INACTIVE;
    }
}
