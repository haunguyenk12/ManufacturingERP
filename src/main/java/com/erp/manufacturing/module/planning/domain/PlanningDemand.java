package com.erp.manufacturing.module.planning.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "planning_demands", indexes = {
        @Index(name = "idx_planning_demands_company_plant_status_due", columnList = "company_id, plant_id, status, due_date"),
        @Index(name = "idx_planning_demands_item_id", columnList = "item_id"),
        @Index(name = "idx_planning_demands_warehouse_id", columnList = "warehouse_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanningDemand extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "planning_demand_id", updatable = false, nullable = false)
    private UUID planningDemandId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "demand_type", nullable = false, length = 30)
    private PlanningDemandType demandType;

    @Column(name = "required_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal requiredQuantity;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PlanningDemandStatus status = PlanningDemandStatus.OPEN;

    @Column(name = "reference_type", length = 80)
    private String referenceType;

    @Column(name = "reference_id", length = 120)
    private String referenceId;

    public boolean isOpen() {
        return status == PlanningDemandStatus.OPEN;
    }

    public void cancel() {
        status = PlanningDemandStatus.CANCELLED;
    }
}
