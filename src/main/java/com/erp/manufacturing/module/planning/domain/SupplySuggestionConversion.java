package com.erp.manufacturing.module.planning.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One converted entry of a commercial run (V75, D5): {@code quantity} of a requirement's proposal
 * became the draft document {@code targetId}. The ledger is what makes a conversion partial and
 * repeatable — {@code remainingConvertible = suggestedQuantity - SUM(quantity)} — and replay-safe:
 * rows written under an {@code Idempotency-Key} are found again by {@code (run, key)}.
 *
 * <p>Never updated or deleted. Cancelling the draft document later does <em>not</em> return its
 * quantity (FE Q21): that needs an explicit reverse command, which does not exist yet.
 */
@Entity
@Table(name = "supply_suggestion_conversions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplySuggestionConversion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "conversion_id", updatable = false, nullable = false)
    private UUID conversionId;

    @Column(name = "mrp_run_id", nullable = false)
    private UUID mrpRunId;

    @Column(name = "supply_suggestion_id", nullable = false)
    private UUID supplySuggestionId;

    @Column(name = "requirement_line_id", nullable = false)
    private UUID requirementLineId;

    @Column(name = "conversion_batch_id", nullable = false)
    private UUID conversionBatchId;

    @Column(name = "entry_no", nullable = false)
    private Integer entryNo;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private SupplyConversionTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "target_line_id")
    private UUID targetLineId;

    @Column(name = "target_code", nullable = false, length = 100)
    private String targetCode;

    @Column(name = "conversion_version", nullable = false)
    private Integer conversionVersion;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;
}
