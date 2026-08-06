package com.erp.manufacturing.module.inventory.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "serial_numbers", indexes = {
        @Index(name = "idx_serial_numbers_item_id", columnList = "item_id"),
        @Index(name = "idx_serial_numbers_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SerialNumber extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "serial_id", updatable = false, nullable = false)
    private UUID serialId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "serial_code", nullable = false, length = 120)
    private String serialCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SerialStatus status = SerialStatus.AVAILABLE;

    @Column(name = "received_at")
    private Instant receivedAt;

    public boolean canIssue() {
        return status == SerialStatus.AVAILABLE;
    }
}
