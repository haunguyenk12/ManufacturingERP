package com.erp.manufacturing.module.inventory.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_lots", indexes = {
        @Index(name = "idx_inventory_lots_item_id", columnList = "item_id"),
        @Index(name = "idx_inventory_lots_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "lot_id", updatable = false, nullable = false)
    private UUID lotId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "lot_code", nullable = false, length = 120)
    private String lotCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LotStatus status = LotStatus.AVAILABLE;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public boolean canIssue() {
        return status == LotStatus.AVAILABLE;
    }
}
