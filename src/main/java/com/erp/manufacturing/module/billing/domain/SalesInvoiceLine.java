package com.erp.manufacturing.module.billing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G10c: one POSTED delivery line billed by an invoice (V88), with the price and the cumulative base it was priced on:
 * {@code amount = ROUND_HALF_UP((billedQuantityBefore + quantity) × unitPrice) − billedAmountBefore}.
 */
@Entity
@Table(name = "sales_invoice_lines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoiceLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_line_id", updatable = false, nullable = false)
    private UUID invoiceLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private SalesInvoice invoice;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "delivery_line_id", nullable = false)
    private UUID deliveryLineId;

    @Column(name = "sales_order_line_id", nullable = false)
    private UUID salesOrderLineId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Column(name = "billed_quantity_before", nullable = false, precision = 19, scale = 6)
    private BigDecimal billedQuantityBefore;

    @Column(name = "billed_amount_before", nullable = false, precision = 19, scale = 0)
    private BigDecimal billedAmountBefore;
}
