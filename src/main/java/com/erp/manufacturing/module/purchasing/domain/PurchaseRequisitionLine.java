package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "purchase_requisition_lines", indexes = {
        @Index(name = "idx_pr_lines_requisition_id", columnList = "purchase_requisition_id"),
        @Index(name = "idx_pr_lines_item_id", columnList = "item_id"),
        @Index(name = "idx_pr_lines_supplier_id", columnList = "supplier_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "purchase_requisition_line_id", updatable = false, nullable = false)
    private UUID purchaseRequisitionLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_requisition_id", nullable = false)
    private PurchaseRequisition purchaseRequisition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "requested_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal requestedQuantity;

    @Column(name = "approved_quantity", precision = 19, scale = 6)
    private BigDecimal approvedQuantity;

    @Column(name = "needed_by_date", nullable = false)
    private LocalDate neededByDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public BigDecimal effectiveApprovedQuantity() {
        return approvedQuantity == null ? requestedQuantity : approvedQuantity;
    }
}
