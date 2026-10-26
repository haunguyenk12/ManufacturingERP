package com.erp.manufacturing.module.delivery.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * G10b: one dispatch of a sales order's goods to its master customer (V87). The lines are read and written through
 * {@code DeliveryLineRepository}, not a mapped collection: a draft replaces all of them and an orphan-removal
 * collection would insert the new {@code line_no}s before deleting the old ones (§0.40).
 */
@Entity
@Table(name = "deliveries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Delivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "delivery_id", updatable = false, nullable = false)
    private UUID deliveryId;

    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private String code;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "plant_id", nullable = false, updatable = false)
    private UUID plantId;

    @Column(name = "sales_order_id", nullable = false, updatable = false)
    private UUID salesOrderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    /** Snapshot of the customer's name when the delivery was created. */
    @Column(name = "customer_name", nullable = false, updatable = false, length = 255)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.DRAFT;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by")
    private UUID postedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    public boolean isDraft() {
        return status == DeliveryStatus.DRAFT;
    }

    public void post(Instant at, UUID by) {
        status = DeliveryStatus.POSTED;
        postedAt = at;
        postedBy = by;
    }

    public void cancel(Instant at, UUID by, String reason) {
        status = DeliveryStatus.CANCELLED;
        cancelledAt = at;
        cancelledBy = by;
        cancelReason = reason;
    }
}
