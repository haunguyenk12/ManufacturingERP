package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.SerialNumber;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "material_issue_lines", indexes = {
        @Index(name = "idx_material_issue_lines_issue_id", columnList = "issue_id"),
        @Index(name = "idx_material_issue_lines_component_line_id", columnList = "component_line_id"),
        @Index(name = "idx_material_issue_lines_reservation_id", columnList = "reservation_id"),
        @Index(name = "idx_material_issue_lines_stock_movement_id", columnList = "stock_movement_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialIssueLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "issue_line_id", updatable = false, nullable = false)
    private UUID issueLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private MaterialIssue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_line_id", nullable = false)
    private WorkOrderComponentLine componentLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private MaterialReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serial_id")
    private SerialNumber serial;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    /** True when this line issued more than the component's remaining BOM requirement. */
    @Column(name = "over_issue", nullable = false)
    @Builder.Default
    private boolean overIssue = false;

    /** Justification captured from the user holding {@code PERM_MATERIAL_ISSUE_OVERRIDE}. */
    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_movement_id", nullable = false)
    private StockMovement stockMovement;
}
