package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goods_receipt_lines", indexes = {
        @Index(name = "idx_gr_lines_receipt_id", columnList = "goods_receipt_id"),
        @Index(name = "idx_gr_lines_po_line_id", columnList = "purchase_order_line_id"),
        @Index(name = "idx_gr_lines_stock_movement_id", columnList = "stock_movement_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "goods_receipt_line_id", updatable = false, nullable = false)
    private UUID goodsReceiptLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_line_id", nullable = false)
    private PurchaseOrderLine purchaseOrderLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    @Column(name = "lot_code", length = 120)
    private String lotCode;

    @Column(name = "received_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal receivedQuantity;

    /** {@code null} while the receipt is DRAFT (V77); set by posting. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_movement_id")
    private StockMovement stockMovement;

    /** G7b: expiry of the new lot, a plant-local date; only lot-tracked lines may carry one. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    /** G7c (V78): running sum of the accepted quantity of every purchase QC decision on this line. */
    @Column(name = "accepted_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal acceptedQuantity = BigDecimal.ZERO;

    /** G7c (V78): running sum of the rejected quantity. {@code accepted + rejected <= received} is a CHECK. */
    @Column(name = "rejected_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal rejectedQuantity = BigDecimal.ZERO;

    /** What purchase QC has not decided yet: {@code received − accepted − rejected}. */
    public BigDecimal pendingQuantity() {
        return receivedQuantity.subtract(acceptedQuantity).subtract(rejectedQuantity);
    }

    public boolean hasQualityDecisions() {
        return acceptedQuantity.signum() > 0 || rejectedQuantity.signum() > 0;
    }

    public void recordQualityDecision(BigDecimal accepted, BigDecimal rejected) {
        this.acceptedQuantity = acceptedQuantity.add(accepted);
        this.rejectedQuantity = rejectedQuantity.add(rejected);
    }
}
