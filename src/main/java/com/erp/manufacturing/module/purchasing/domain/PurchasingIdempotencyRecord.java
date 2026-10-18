package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * G7a: one {@code Idempotency-Key} used on a purchasing command (V76). The commands change an
 * existing document rather than create a ledger row, so the key cannot live on the document itself
 * like {@code goods_receipts.idempotency_key}; it lives here, scoped by command. Never expires.
 */
@Entity
@Table(name = "purchasing_idempotency_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchasingIdempotencyRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "record_id", updatable = false, nullable = false)
    private UUID recordId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "command", nullable = false, length = 40)
    private PurchasingCommand command;

    /** The document the command ran on (or created). A replay answers with its current state. */
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;
}
