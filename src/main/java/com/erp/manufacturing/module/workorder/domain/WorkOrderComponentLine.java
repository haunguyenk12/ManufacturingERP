package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.inventory.domain.Item;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "work_order_component_lines", indexes = {
        @Index(name = "idx_work_order_component_work_order_id", columnList = "work_order_id"),
        @Index(name = "idx_work_order_component_item_id", columnList = "component_item_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderComponentLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "component_line_id", updatable = false, nullable = false)
    private UUID componentLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_line_id", nullable = false)
    private BomLine bomLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_item_id", nullable = false)
    private Item componentItem;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "quantity_per", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityPer;

    @Column(name = "scrap_rate", nullable = false, precision = 9, scale = 6)
    @Builder.Default
    private BigDecimal scrapRate = BigDecimal.ZERO;

    @Column(name = "required_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal requiredQuantity;

    @Column(name = "issued_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal issuedQuantity = BigDecimal.ZERO;

    public BigDecimal remainingQuantity() {
        return requiredQuantity.subtract(issuedQuantity);
    }

    public void addIssuedQuantity(BigDecimal quantity) {
        issuedQuantity = issuedQuantity.add(quantity);
    }
}
