package com.erp.manufacturing.module.routing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "routings", indexes = {
        @Index(name = "idx_routings_company_id", columnList = "company_id"),
        @Index(name = "idx_routings_item_id", columnList = "item_id"),
        @Index(name = "idx_routings_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingHeader extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "routing_id", updatable = false, nullable = false)
    private UUID routingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** The manufactured item this routing produces — same role as {@code BomHeader.parentItem}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    /**
     * The business revision of this routing (spec: {@code sourceRoutingVersion}).
     * Named {@code routingVersion} — not {@code version} — because {@link BaseEntity} already owns
     * {@code version} for optimistic locking.
     */
    @Column(name = "routing_version", nullable = false, length = 40)
    private String routingVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RoutingStatus status = RoutingStatus.DRAFT;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "routing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<RoutingOperation> operations = new ArrayList<>();

    public boolean isDraft() {
        return status == RoutingStatus.DRAFT;
    }

    public boolean isActive() {
        return status == RoutingStatus.ACTIVE;
    }

    public void activate() {
        status = RoutingStatus.ACTIVE;
    }

    public void deactivate() {
        status = RoutingStatus.INACTIVE;
    }
}
