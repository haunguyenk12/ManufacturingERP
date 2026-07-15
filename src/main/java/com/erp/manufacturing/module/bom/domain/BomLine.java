package com.erp.manufacturing.module.bom.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bom_lines", indexes = {
        @Index(name = "idx_bom_lines_bom_id", columnList = "bom_id"),
        @Index(name = "idx_bom_lines_component_item_id", columnList = "component_item_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BomLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "line_id", updatable = false, nullable = false)
    private UUID lineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_id", nullable = false)
    private BomHeader bom;

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
}
