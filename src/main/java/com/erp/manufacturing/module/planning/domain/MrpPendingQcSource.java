package com.erp.manufacturing.module.planning.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CF-4 (V93, B267): one goods receipt line that held pending purchase-QC quantity of a requirement's item and
 * warehouse when the run ran. A snapshot — written once with the run, never updated, so QC decided later does not
 * rewrite what an old run showed (FE R05). Receipt references are flat ids: planning reads purchasing through its
 * lookup service (C7), not through an association.
 */
@Entity
@Table(name = "mrp_pending_qc_sources")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MrpPendingQcSource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "source_id", updatable = false, nullable = false)
    private UUID sourceId;

    @Column(name = "mrp_run_id", nullable = false)
    private UUID mrpRunId;

    @Column(name = "requirement_line_id", nullable = false)
    private UUID requirementLineId;

    @Column(name = "goods_receipt_id", nullable = false)
    private UUID goodsReceiptId;

    @Column(name = "goods_receipt_line_id", nullable = false)
    private UUID goodsReceiptLineId;

    @Column(name = "receipt_code", nullable = false, length = 100)
    private String receiptCode;

    @Column(name = "receipt_version", nullable = false)
    private Long receiptVersion;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "uom_code", nullable = false, length = 30)
    private String uomCode;

    @Column(name = "pending_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal pendingQuantity;
}
