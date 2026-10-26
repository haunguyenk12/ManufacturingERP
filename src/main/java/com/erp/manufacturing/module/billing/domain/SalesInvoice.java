package com.erp.manufacturing.module.billing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * G10c: an internal sales invoice of one order's POSTED delivery lines (V88), VND, whole dong. {@code amount} is the
 * sum of the lines; {@code billingSnapshot} is the opaque base the lines were priced on (FE R14, Q22). Lines go
 * through {@code SalesInvoiceLineRepository}, not a mapped collection (full replacement, §0.40).
 */
@Entity
@Table(name = "sales_invoices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_id", updatable = false, nullable = false)
    private UUID invoiceId;

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

    @Column(name = "customer_name", nullable = false, updatable = false, length = 255)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SalesInvoiceStatus status = SalesInvoiceStatus.DRAFT;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Column(name = "billing_snapshot", nullable = false, length = 80)
    private String billingSnapshot;

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
        return status == SalesInvoiceStatus.DRAFT;
    }

    public boolean isPosted() {
        return status == SalesInvoiceStatus.POSTED;
    }

    public void post(Instant at, UUID by) {
        status = SalesInvoiceStatus.POSTED;
        postedAt = at;
        postedBy = by;
    }

    public void cancel(Instant at, UUID by, String reason) {
        status = SalesInvoiceStatus.CANCELLED;
        cancelledAt = at;
        cancelledBy = by;
        cancelReason = reason;
    }
}
