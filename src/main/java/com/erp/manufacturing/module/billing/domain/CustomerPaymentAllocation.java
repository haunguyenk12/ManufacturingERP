package com.erp.manufacturing.module.billing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/** G10d: the part of a payment applied to one POSTED invoice (V89), whole dong, > 0. */
@Entity
@Table(name = "customer_payment_allocations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPaymentAllocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "allocation_id", updatable = false, nullable = false)
    private UUID allocationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private CustomerPayment payment;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;
}
