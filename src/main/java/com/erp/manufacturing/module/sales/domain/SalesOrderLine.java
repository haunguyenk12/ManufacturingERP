package com.erp.manufacturing.module.sales.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sales_order_lines", indexes = {
        @Index(name = "idx_sales_order_lines_order_id", columnList = "sales_order_id"),
        @Index(name = "idx_sales_order_lines_item_id", columnList = "item_id"),
        @Index(name = "idx_sales_order_lines_due_date", columnList = "due_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "sales_order_line_id", updatable = false, nullable = false)
    private UUID salesOrderLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "ordered_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal orderedQuantity;

    /**
     * Only ever increased by fulfillment allocation ({@code F6}), and only when a QC disposition of
     * {@code AVAILABLE} commits — see {@code module/sales/CLAUDE.md} B45.
     */
    @Column(name = "fulfilled_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal fulfilledQuantity = BigDecimal.ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    public BigDecimal openQuantity() {
        return orderedQuantity.subtract(fulfilledQuantity);
    }

    public boolean isFullyFulfilled() {
        return fulfilledQuantity.compareTo(orderedQuantity) >= 0;
    }

    /**
     * Adds fulfilled quantity, never past {@code orderedQuantity} (B45). Output beyond what the
     * customer ordered is free stock, not an error — the caller is not told it was clipped.
     */
    public void addFulfilled(BigDecimal quantity) {
        fulfilledQuantity = fulfilledQuantity.add(quantity.min(openQuantity()));
    }
}
