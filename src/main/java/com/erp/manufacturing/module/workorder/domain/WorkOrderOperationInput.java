package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Confirmed input of a work order operation (G8c, FE R10). Append-only: the first operation's input comes from the
 * plan ({@code sourceOperationId} null); a later operation's input is a transfer of its predecessor's good output, so
 * the same row is both "input of this operation" and "transferred out of the predecessor".
 */
@Entity
@Table(name = "work_order_operation_inputs", indexes = {
        @Index(name = "idx_operation_inputs_work_order", columnList = "work_order_id"),
        @Index(name = "idx_operation_inputs_operation", columnList = "work_order_operation_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderOperationInput extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "input_id", updatable = false, nullable = false)
    private UUID inputId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_operation_id", nullable = false)
    private WorkOrderOperation operation;

    /** The predecessor whose good output this input transfers; null for the first operation. */
    @Column(name = "source_operation_id")
    private UUID sourceOperationId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;
}
