package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines", indexes = {
        @Index(name = "idx_po_lines_purchase_order_id", columnList = "purchase_order_id"),
        @Index(name = "idx_po_lines_requisition_line_id", columnList = "purchase_requisition_line_id"),
        @Index(name = "idx_po_lines_item_id", columnList = "item_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "purchase_order_line_id", updatable = false, nullable = false)
    private UUID purchaseOrderLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_requisition_line_id")
    private PurchaseRequisitionLine purchaseRequisitionLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "ordered_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal orderedQuantity;

    @Column(name = "received_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(name = "unit_price", precision = 19, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    public BigDecimal remainingQuantity() {
        return orderedQuantity.subtract(receivedQuantity);
    }

    public boolean isFullyReceived() {
        return receivedQuantity.compareTo(orderedQuantity) >= 0;
    }

    public void receive(BigDecimal quantity) {
        receivedQuantity = receivedQuantity.add(quantity);
    }
}
