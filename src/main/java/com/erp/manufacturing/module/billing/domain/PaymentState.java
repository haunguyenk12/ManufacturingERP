package com.erp.manufacturing.module.billing.domain;

/**
 * G10d (FE §3.2): where a POSTED invoice stands with its customer, independent of the invoice status. Derived from the
 * allocations of POSTED payments, never stored.
 */
public enum PaymentState {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    NO_AMOUNT_DUE;

    public static PaymentState of(java.math.BigDecimal amount, java.math.BigDecimal paid) {
        if (amount.signum() == 0) {
            return NO_AMOUNT_DUE;
        }
        if (paid.signum() == 0) {
            return UNPAID;
        }
        return paid.compareTo(amount) >= 0 ? PAID : PARTIALLY_PAID;
    }
}
