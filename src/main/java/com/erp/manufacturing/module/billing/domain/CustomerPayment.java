package com.erp.manufacturing.module.billing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * G10d: money received from one customer of one plant (V89), VND whole dong, allocated to POSTED invoices.
 * {@code amount} equals the sum of its allocations. Allocations go through {@code CustomerPaymentAllocationRepository},
 * not a mapped collection (full replacement, §0.40).
 */
@Entity
@Table(name = "customer_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id", updatable = false, nullable = false)
    private UUID paymentId;

    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private String code;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "plant_id", nullable = false, updatable = false)
    private UUID plantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, updatable = false, length = 255)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CustomerPaymentStatus status = CustomerPaymentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "reference", length = 200)
    private String reference;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

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
        return status == CustomerPaymentStatus.DRAFT;
    }

    public void post(Instant at, UUID by) {
        status = CustomerPaymentStatus.POSTED;
        postedAt = at;
        postedBy = by;
    }

    public void cancel(Instant at, UUID by, String reason) {
        status = CustomerPaymentStatus.CANCELLED;
        cancelledAt = at;
        cancelledBy = by;
        cancelReason = reason;
    }
}
