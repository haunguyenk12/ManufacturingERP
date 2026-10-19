package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * G8: one {@code Idempotency-Key} used on a work order command (V79). The commands change an existing
 * work order rather than create a document row, so the key cannot live on a document like
 * {@code material_issues.idempotency_key}; it lives here, scoped by command. Never expires — same
 * shape as {@code purchasing_idempotency_records} (V76).
 */
@Entity
@Table(name = "work_order_idempotency_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderIdempotencyRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "record_id", updatable = false, nullable = false)
    private UUID recordId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "command", nullable = false, length = 40)
    private WorkOrderCommand command;

    /** The work order or reservation the command ran on (or created). A replay answers with its current state. */
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;
}
