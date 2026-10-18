package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders", indexes = {
        @Index(name = "idx_purchase_orders_company_plant_supplier_status_created", columnList = "company_id, plant_id, supplier_id, status, created_at"),
        @Index(name = "idx_purchase_orders_source_requisition_id", columnList = "source_requisition_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "purchase_order_id", updatable = false, nullable = false)
    private UUID purchaseOrderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "purchase_order_no", nullable = false, length = 100)
    private String purchaseOrderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_requisition_id")
    private PurchaseRequisition sourceRequisition;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** G7a: free supplier/delivery reference, searchable. */
    @Column(name = "reference", length = 200)
    private String reference;

    /** G7a: the reason given when the order was cancelled or closed; {@code null} otherwise. */
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    /** G7a: the status right before cancel/close — history reads it instead of guessing. */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private PurchaseOrderStatus previousStatus;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public boolean isDraft() {
        return status == PurchaseOrderStatus.DRAFT;
    }

    public boolean canReceive() {
        return status == PurchaseOrderStatus.CONFIRMED || status == PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    /** G7a: closing gives up the open remainder of a confirmed order; it needs something still open. */
    public boolean canClose() {
        return canReceive() && lines.stream().anyMatch(line -> line.openQuantity().signum() > 0);
    }

    /**
     * G7a: cancelling is for orders nothing was received on — otherwise close the remainder. The
     * status says it on its own: the first receipt moves a confirmed order to PARTIALLY_RECEIVED, and
     * reversing every receipt brings it back to CONFIRMED ({@link #refreshReceiptStatus()}). A second
     * check on the quantities was tried and removed: no state reaches it (mutation M11, G7a).
     */
    public boolean canCancel() {
        return status == PurchaseOrderStatus.DRAFT || status == PurchaseOrderStatus.CONFIRMED;
    }

    /** Legacy {@code /send} and G7a {@code /confirm} both land here. */
    public void confirm(Instant at, UUID by) {
        status = PurchaseOrderStatus.CONFIRMED;
        confirmedAt = at;
        confirmedBy = by;
    }

    public void cancel(String reason, Instant at, UUID by) {
        previousStatus = status;
        status = PurchaseOrderStatus.CANCELLED;
        statusReason = reason;
        cancelledAt = at;
        cancelledBy = by;
    }

    /** Every line's open remainder becomes {@code closedQuantity}; received quantities do not change. */
    public void close(String reason, Instant at, UUID by) {
        lines.forEach(PurchaseOrderLine::closeRemainder);
        previousStatus = status;
        status = PurchaseOrderStatus.CLOSED;
        statusReason = reason;
        closedAt = at;
        closedBy = by;
    }

    /**
     * Status from the received quantities. A posted receipt moves a confirmed order forward; a
     * cancelled one moves it back, down to {@code CONFIRMED}.
     */
    public void refreshReceiptStatus() {
        boolean anyReceived = lines.stream().anyMatch(line -> line.getReceivedQuantity().signum() > 0);
        boolean allReceived = !lines.isEmpty() && lines.stream().allMatch(PurchaseOrderLine::isFullyReceived);
        if (allReceived) {
            status = PurchaseOrderStatus.FULLY_RECEIVED;
        } else if (anyReceived) {
            status = PurchaseOrderStatus.PARTIALLY_RECEIVED;
        } else {
            status = PurchaseOrderStatus.CONFIRMED;
        }
    }
}
