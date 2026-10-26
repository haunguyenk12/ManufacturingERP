package com.erp.manufacturing.module.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * G10c (FE R14, Q7): whole-dong amounts, rounded HALF_UP on the cumulative quantity of each sales order line — so
 * partial invoices never drift from what one invoice for the total would have said:
 * {@code amount = round((billedBefore + quantity) × price) − billedAmountBefore}. Lines of the same order line are
 * priced in the order given, each moving the running base. Three invoices of 1/3 at 1 dong bill 0, 1, 0.
 */
public final class InvoicePricing {

    /** Largest integer a JSON number carries exactly (D5). */
    public static final BigDecimal MAX_MONEY = BigDecimal.valueOf(9_007_199_254_740_991L);

    private InvoicePricing() {
    }

    /** What POSTED invoices already billed on one order line. */
    public record Base(BigDecimal quantity, BigDecimal amount) {
        public static final Base NONE = new Base(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public record Input(UUID salesOrderLineId, BigDecimal quantity, BigDecimal unitPrice) {
    }

    public record Priced(BigDecimal amount, BigDecimal billedQuantityBefore, BigDecimal billedAmountBefore) {
    }

    public static List<Priced> price(List<Input> inputs, Map<UUID, Base> bases) {
        Map<UUID, Base> running = new HashMap<>();
        List<Priced> priced = new ArrayList<>();
        for (Input input : inputs) {
            Base before = running.getOrDefault(input.salesOrderLineId(),
                    bases.getOrDefault(input.salesOrderLineId(), Base.NONE));
            BigDecimal quantity = before.quantity().add(input.quantity());
            BigDecimal cumulative = quantity.multiply(input.unitPrice()).setScale(0, RoundingMode.HALF_UP);
            BigDecimal amount = cumulative.subtract(before.amount());
            priced.add(new Priced(amount, before.quantity(), before.amount()));
            running.put(input.salesOrderLineId(), new Base(quantity, cumulative));
        }
        return priced;
    }

    /**
     * Opaque: the order's pricing version and the base of every order line the invoice touches. Equal snapshots mean
     * equal amounts for the same lines; anything that would change an amount changes the snapshot (FE Q22).
     */
    public static String snapshot(String pricingVersion, Map<UUID, Base> touchedBases) {
        StringBuilder canonical = new StringBuilder(pricingVersion);
        for (Map.Entry<UUID, Base> entry : new TreeMap<>(touchedBases).entrySet()) {
            canonical.append('|').append(entry.getKey())
                    .append(':').append(plain(entry.getValue().quantity()))
                    .append(':').append(plain(entry.getValue().amount()));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return "BILL-" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String plain(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
