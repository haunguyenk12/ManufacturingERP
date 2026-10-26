package com.erp.manufacturing.module.billing.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** G10c (FE R14, Q7): cumulative HALF_UP per order line, and the snapshot that says when the base moved. */
class InvoicePricingTest {

    private static final UUID LINE = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void threePartialInvoicesOfAThird_billZeroOneZero_andAddUpToTheWhole() {
        BigDecimal price = new BigDecimal("1");
        InvoicePricing.Base base = InvoicePricing.Base.NONE;
        StringBuilder amounts = new StringBuilder();
        for (String quantity : List.of("0.333333", "0.333333", "0.333334")) {
            InvoicePricing.Priced priced = InvoicePricing.price(
                    List.of(new InvoicePricing.Input(LINE, new BigDecimal(quantity), price)), Map.of(LINE, base)).get(0);
            amounts.append(priced.amount().toPlainString()).append(' ');
            base = new InvoicePricing.Base(base.quantity().add(new BigDecimal(quantity)), base.amount().add(priced.amount()));
        }
        assertThat(amounts.toString().trim()).isEqualTo("0 1 0");
        assertThat(base.amount()).isEqualByComparingTo("1");
    }

    @Test
    void linesOfTheSameOrderLine_moveTheRunningBaseInTheGivenOrder() {
        BigDecimal price = new BigDecimal("100000");
        List<InvoicePricing.Priced> priced = InvoicePricing.price(List.of(
                new InvoicePricing.Input(LINE, new BigDecimal("3"), price),
                new InvoicePricing.Input(LINE, new BigDecimal("1"), price),
                new InvoicePricing.Input(LINE, new BigDecimal("4"), price)), Map.of());
        assertThat(priced).extracting(p -> p.amount().toPlainString()).containsExactly("300000", "100000", "400000");
        assertThat(priced.get(2).billedQuantityBefore()).isEqualByComparingTo("4");
        assertThat(priced.get(2).billedAmountBefore()).isEqualByComparingTo("400000");
    }

    @Test
    void theBaseOfPostedInvoicesIsWhereTheRunningTotalStarts_perOrderLine() {
        List<InvoicePricing.Priced> priced = InvoicePricing.price(List.of(
                new InvoicePricing.Input(LINE, new BigDecimal("0.5"), new BigDecimal("1")),
                new InvoicePricing.Input(OTHER, new BigDecimal("0.5"), new BigDecimal("1"))),
                Map.of(LINE, new InvoicePricing.Base(new BigDecimal("0.5"), new BigDecimal("1"))));
        // LINE: round(1.0) − 1 = 0; OTHER starts from nothing: round(0.5) = 1 (HALF_UP)
        assertThat(priced).extracting(p -> p.amount().toPlainString()).containsExactly("0", "1");
    }

    @Test
    void snapshot_dependsOnTheBaseAndThePricingVersion_notOnHowTheNumberIsScaled() {
        String a = InvoicePricing.snapshot("PRICE-1",
                Map.of(LINE, new InvoicePricing.Base(new BigDecimal("2.000000"), new BigDecimal("200000"))));
        String sameValue = InvoicePricing.snapshot("PRICE-1",
                Map.of(LINE, new InvoicePricing.Base(new BigDecimal("2"), new BigDecimal("200000"))));
        String moved = InvoicePricing.snapshot("PRICE-1",
                Map.of(LINE, new InvoicePricing.Base(new BigDecimal("3"), new BigDecimal("300000"))));
        String repriced = InvoicePricing.snapshot("PRICE-2",
                Map.of(LINE, new InvoicePricing.Base(new BigDecimal("2"), new BigDecimal("200000"))));
        assertThat(a).startsWith("BILL-").isEqualTo(sameValue).isNotEqualTo(moved).isNotEqualTo(repriced);
    }
}
