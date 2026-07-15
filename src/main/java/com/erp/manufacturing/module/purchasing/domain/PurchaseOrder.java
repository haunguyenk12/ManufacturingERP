package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

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

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public boolean isDraft() {
        return status == PurchaseOrderStatus.DRAFT;
    }

    public boolean canReceive() {
        return status == PurchaseOrderStatus.SENT || status == PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    public void send() {
        status = PurchaseOrderStatus.SENT;
    }

    public void cancel() {
        status = PurchaseOrderStatus.CANCELLED;
    }

    public void refreshReceiptStatus() {
        boolean anyReceived = lines.stream().anyMatch(line -> line.getReceivedQuantity().signum() > 0);
        boolean allReceived = !lines.isEmpty() && lines.stream().allMatch(PurchaseOrderLine::isFullyReceived);
        if (allReceived) {
            status = PurchaseOrderStatus.RECEIVED;
        } else if (anyReceived) {
            status = PurchaseOrderStatus.PARTIALLY_RECEIVED;
        }
    }
}
