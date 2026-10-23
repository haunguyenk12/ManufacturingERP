package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G9b (V84): the part of one final-good production execution a production receipt claims (FE R05). Allocated by the
 * server, oldest report first, when the receipt is created and on every draft edit — never sent by a client (Q6). A
 * receipt counts against its executions while it is DRAFT, PENDING_APPROVAL or APPROVED; rejected and cancelled
 * receipts keep their rows as history.
 */
@Entity
@Table(name = "production_receipt_contributions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionReceiptContribution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "contribution_id", updatable = false, nullable = false)
    private UUID contributionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false, updatable = false)
    private ProductionReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_execution_id", nullable = false, updatable = false)
    private ProductionExecution execution;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;
}
