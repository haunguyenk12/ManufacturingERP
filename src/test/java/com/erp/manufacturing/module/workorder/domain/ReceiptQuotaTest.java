package com.erp.manufacturing.module.workorder.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** G9a (FE §3.1): G, C = min(P, G), A, D, W, U — numbers asserted as the FE fixture F1 states them. */
class ReceiptQuotaTest {

    private static ReceiptQuota quota(String planned, String good, String approved, String claimed) {
        return new ReceiptQuota(new BigDecimal(planned), new BigDecimal(good), new BigDecimal(approved),
                new BigDecimal(claimed));
    }

    @Test
    void fixtureF1_draftOfFive_leavesFourUnclaimedAndNineAwaiting() {
        ReceiptQuota quota = quota("10", "9", "0", "5");

        assertThat(quota.cap()).isEqualByComparingTo("9");
        assertThat(quota.awaiting()).isEqualByComparingTo("9");
        assertThat(quota.unclaimed()).isEqualByComparingTo("4");
    }

    @Test
    void fixtureF1_afterBothApprovals_nothingIsLeft() {
        ReceiptQuota quota = quota("10", "9", "9", "0");

        assertThat(quota.awaiting()).isEqualByComparingTo("0");
        assertThat(quota.unclaimed()).isEqualByComparingTo("0");
    }

    /** C = min(P, G) — G never exceeds P (V32), so the cap is the final good. */
    @Test
    void capIsTheFinalGoodBelowThePlan() {
        assertThat(quota("10", "7", "0", "0").cap()).isEqualByComparingTo("7");
    }

    /** Editing a draft gives its own claim back first (FE R04). */
    @Test
    void unclaimedExcluding_givesTheOwnClaimBack() {
        ReceiptQuota quota = quota("10", "9", "0", "5");

        assertThat(quota.unclaimedExcluding(new BigDecimal("5"))).isEqualByComparingTo("9");
    }

    /** FE F3: exact six decimals, no rounding. */
    @Test
    void exactSixDecimals() {
        ReceiptQuota quota = quota("10", "0.000002", "0", "0.000001");

        assertThat(quota.unclaimed()).isEqualByComparingTo("0.000001");
    }
}
