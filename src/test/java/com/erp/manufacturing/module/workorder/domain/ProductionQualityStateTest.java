package com.erp.manufacturing.module.workorder.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** G9c (FE Q20): the quality state of a receipt, derived from its line totals. */
class ProductionQualityStateTest {

    private static List<ProductionReceiptLine> line(String quantity, String accepted, String rejected) {
        ProductionReceiptLine line = ProductionReceiptLine.builder().quantity(new BigDecimal(quantity)).build();
        line.recordQualityDecision(new BigDecimal(accepted), new BigDecimal(rejected));
        return List.of(line);
    }

    private static ProductionQualityState state(String quantity, String accepted, String rejected) {
        return ProductionReceipt.qualityState(ProductionReceiptStatus.APPROVED, line(quantity, accepted, rejected));
    }

    @Test
    void notApproved_hasNoQualityStateAndNothingPending() {
        assertThat(ProductionReceipt.qualityState(ProductionReceiptStatus.DRAFT, line("5", "0", "0"))).isNull();
        assertThat(ProductionReceipt.pendingQcQuantity(ProductionReceiptStatus.DRAFT, line("5", "0", "0")))
                .isEqualByComparingTo("0");
    }

    @Test
    void approvedWithoutDecision_isNotStarted() {
        assertThat(state("5", "0", "0")).isEqualTo(ProductionQualityState.NOT_STARTED);
        assertThat(ProductionReceipt.pendingQcQuantity(ProductionReceiptStatus.APPROVED, line("5", "0", "0")))
                .isEqualByComparingTo("5");
    }

    /** Something pending stays PARTIAL even when part was rejected — the totals keep the rejection visible. */
    @Test
    void somethingStillPending_isPartialEvenWithARejection() {
        assertThat(state("5", "3", "1")).isEqualTo(ProductionQualityState.PARTIAL);
    }

    @Test
    void nothingPending_isAcceptedRejectedOrMixed() {
        assertThat(state("5", "5", "0")).isEqualTo(ProductionQualityState.ACCEPTED);
        assertThat(state("5", "0", "5")).isEqualTo(ProductionQualityState.REJECTED);
        assertThat(state("5", "4", "1")).isEqualTo(ProductionQualityState.MIXED);
    }
}
