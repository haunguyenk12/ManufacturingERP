package com.erp.manufacturing.module.routing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
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
     * Free-text work center reference. Work Center is deliberately NOT an entity in this phase —
     * the MVP has no work center screen and capacity/CRP is out of scope (spec §11).
     */
    @Column(name = "work_center_code", nullable = false, length = 100)
    private String workCenterCode;

    @Column(name = "setup_minutes", nullable = false, precision = 19, scale = 6)
    private BigDecimal setupMinutes;

    @Column(name = "run_minutes_per_unit", nullable = false, precision = 19, scale = 6)
    private BigDecimal runMinutesPerUnit;
}
