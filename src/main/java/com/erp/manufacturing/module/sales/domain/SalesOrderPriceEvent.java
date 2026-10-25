package com.erp.manufacturing.module.sales.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G10a: one price set on a line of a confirmed order through {@code POST …/commercial-prices} (V86). Written once,
 * never updated or deleted; who and when come from the audit columns.
 */
@Entity
@Table(name = "sales_order_price_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderPriceEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "sales_order_id", nullable = false, updatable = false)
    private UUID salesOrderId;

    @Column(name = "sales_order_line_id", nullable = false, updatable = false)
    private UUID salesOrderLineId;

    @Column(name = "previous_unit_price", precision = 19, scale = 6, updatable = false)
    private BigDecimal previousUnitPrice;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "pricing_revision", nullable = false, updatable = false)
    private int pricingRevision;
}
