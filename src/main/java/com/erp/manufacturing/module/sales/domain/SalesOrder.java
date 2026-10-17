package com.erp.manufacturing.module.sales.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales_orders", indexes = {
        @Index(name = "idx_sales_orders_company_plant_status", columnList = "company_id, plant_id, status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "sales_order_id", updatable = false, nullable = false)
    private UUID salesOrderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "order_no", nullable = false, length = 100)
    private String orderNo;

    /**
     * Snapshot of the customer's name when the order was last saved as a draft (FE Q18): renaming the
     * customer later must not rewrite order history. Free text on orders created through the legacy
     * {@code POST /v1} path, which has no {@link #customerId}.
     */
    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    /**
     * G6a. {@code null} on every order created before customers were linked (V74 does not backfill —
     * matching free-text names against the master would be a guess, G2 §9.5) and on orders still
     * created through the legacy path. Plain UUID, like the rest of this module's cross-aggregate links.
     */
    @Column(name = "customer_id")
    private UUID customerId;

    /** Snapshot of the customer code, saved alongside {@link #customerName}. */
    @Column(name = "customer_code", length = 100)
    private String customerCode;

    /** The customer's own reference (their PO number). Free text, not unique, not an idempotency key. */
    @Column(name = "customer_reference", length = 255)
    private String customerReference;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private SalesOrderStatus status = SalesOrderStatus.DRAFT;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalesOrderLine> lines = new ArrayList<>();

    public boolean isDraft() {
        return status == SalesOrderStatus.DRAFT;
    }

    public boolean isConfirmed() {
        return status == SalesOrderStatus.CONFIRMED;
    }

    public void confirm() {
        status = SalesOrderStatus.CONFIRMED;
    }

    public void cancel() {
        status = SalesOrderStatus.CANCELLED;
    }

    /**
     * Entered when the first work order is allocated against one of this order's lines (F6). Only
     * from {@code CONFIRMED}: an order already reporting fulfilled quantity must not fall back to
     * "production started".
     */
    public void markInProduction() {
        if (status == SalesOrderStatus.CONFIRMED) {
            status = SalesOrderStatus.IN_PRODUCTION;
        }
    }

    public void markPartiallyFulfilled() {
        status = SalesOrderStatus.PARTIALLY_FULFILLED;
    }

    public void markFulfilled() {
        status = SalesOrderStatus.FULFILLED;
    }
}
