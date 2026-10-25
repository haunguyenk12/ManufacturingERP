package com.erp.manufacturing.module.sales.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * G10 (D9): one {@code Idempotency-Key} used on a sales order, delivery, invoice or payment command (V86). Same
 * shape as {@code work_order_idempotency_records}: scoped by command, never expires.
 */
@Entity
@Table(name = "commercial_idempotency_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialIdempotencyRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "record_id", updatable = false, nullable = false)
    private UUID recordId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "command", nullable = false, length = 40)
    private CommercialCommand command;

    /** The document the command ran on or created. A replay answers with its current state. */
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;
}
