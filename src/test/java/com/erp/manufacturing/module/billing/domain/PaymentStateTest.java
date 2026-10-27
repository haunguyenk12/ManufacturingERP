package com.erp.manufacturing.module.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** G10d (FE §3.2, Q8): a zero invoice has nothing due — it is never "unpaid" and needs no fake 0-dong payment. */
class PaymentStateTest {

    @Test
    void stateFollowsWhatPostedPaymentsAllocated() {
        assertThat(PaymentState.of(new BigDecimal("800000"), BigDecimal.ZERO)).isEqualTo(PaymentState.UNPAID);
        assertThat(PaymentState.of(new BigDecimal("800000"), new BigDecimal("300000"))).isEqualTo(PaymentState.PARTIALLY_PAID);
        assertThat(PaymentState.of(new BigDecimal("800000"), new BigDecimal("800000"))).isEqualTo(PaymentState.PAID);
    }

    @Test
    void aZeroInvoiceHasNoAmountDue() {
        assertThat(PaymentState.of(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualTo(PaymentState.NO_AMOUNT_DUE);
    }
}
