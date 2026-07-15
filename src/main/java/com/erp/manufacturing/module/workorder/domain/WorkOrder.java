package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "work_orders", indexes = {
        @Index(name = "idx_work_orders_company_id", columnList = "company_id"),
        @Index(name = "idx_work_orders_plant_id", columnList = "plant_id"),
        @Index(name = "idx_work_orders_product_item_id", columnList = "product_item_id"),
        @Index(name = "idx_work_orders_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "work_order_id", updatable = false, nullable = false)
    private UUID workOrderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "work_order_no", nullable = false, length = 100)
    private String workOrderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_item_id", nullable = false)
    private Item productItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_id", nullable = false)
    private BomHeader bom;

    @Column(name = "bom_revision", nullable = false, length = 40)
    private String bomRevision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "output_warehouse_id", nullable = false)
    private Warehouse outputWarehouse;

    @Column(name = "planned_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal plannedQuantity;

    @Column(name = "completed_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal completedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WorkOrderStatus status = WorkOrderStatus.DRAFT;

    @Column(name = "planned_start_at")
    private Instant plannedStartAt;

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<WorkOrderComponentLine> componentLines = new ArrayList<>();

    public boolean isDraft() {
        return status == WorkOrderStatus.DRAFT;
    }

    public boolean canExecute() {
        return status == WorkOrderStatus.RELEASED || status == WorkOrderStatus.IN_PROGRESS;
    }

    public void release(Instant now) {
        status = WorkOrderStatus.RELEASED;
        releasedAt = now;
    }

    public void markInProgress() {
        if (status == WorkOrderStatus.RELEASED) {
            status = WorkOrderStatus.IN_PROGRESS;
        }
    }

    public void complete(Instant now) {
        status = WorkOrderStatus.COMPLETED;
        completedAt = now;
    }

    public void cancel(Instant now) {
        status = WorkOrderStatus.CANCELLED;
        cancelledAt = now;
    }
}
