package com.erp.manufacturing.module.bom.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import jakarta.persistence.*;
import lombok.*;

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
