package com.erp.manufacturing.module.routing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "routing_operations", indexes = {
        @Index(name = "idx_routing_operations_routing_id", columnList = "routing_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingOperation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "routing_operation_id", updatable = false, nullable = false)
    private UUID routingOperationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routing_id", nullable = false)
    private RoutingHeader routing;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * FK to Work Center (C2-6). Nullable: rows created before this phase have no work center to
     * point at (the old free-text {@code work_center_code} carried no plant, so it cannot be
     * backfilled — see {@code module/workcenter/CLAUDE.md} bất biến {@code B_wc2}). Every row
     * created through {@link com.erp.manufacturing.module.routing.service.RoutingService} from now
     * on is required to have one ({@code RoutingOperationRequest.workCenterId} is {@code @NotNull}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_center_id")
    private WorkCenter workCenter;

    @Column(name = "setup_minutes", nullable = false, precision = 19, scale = 6)
    private BigDecimal setupMinutes;

    @Column(name = "run_minutes_per_unit", nullable = false, precision = 19, scale = 6)
    private BigDecimal runMinutesPerUnit;
}
