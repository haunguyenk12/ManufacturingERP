package com.erp.manufacturing.module.workorder.service.cost;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * G11: the rounding rules of production cost (FE §3.5). Money is {@code C6(x) = HALF_UP(x, 6)}; unit costs and minutes
 * keep 12 decimals (FE Q4). Intermediate products use 34 significant digits, so nothing is rounded before the one
 * rounding that decides an amount — never {@code double}, never "unit cost × quantity" to decide a total (FE Q19).
 */
public final class CostMath {

    public static final MathContext CONTEXT = MathContext.DECIMAL128;
    public static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Largest amount a {@code NUMERIC(19,6)} column holds (D7). */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.999999");

    private CostMath() {
    }

    public static BigDecimal c6(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    public static BigDecimal c12(BigDecimal value) {
        return value == null ? null : value.setScale(12, RoundingMode.HALF_UP);
    }

    /** {@code C6(minutes × hourlyRate / 60)}; unknown when either side is unknown. */
    public static BigDecimal minutesCost(BigDecimal minutes, BigDecimal hourlyRate) {
        if (minutes == null || hourlyRate == null) {
            return null;
        }
        return c6(minutes.multiply(hourlyRate, CONTEXT).divide(SIXTY, CONTEXT));
    }

    /** {@code C6(total × part / whole)} — the cumulative share of a total (FE §3.5 #8). */
    public static BigDecimal share(BigDecimal total, BigDecimal part, BigDecimal whole) {
        return c6(total.multiply(part, CONTEXT).divide(whole, CONTEXT));
    }

    /** Sum of possibly-unknown amounts: unknown as soon as one is unknown (FE §3.5 #6). */
    public static BigDecimal sumKnown(Iterable<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value == null) {
                return null;
            }
            total = total.add(value);
        }
        return c6(total);
    }

    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? null : c6(left.add(right));
    }

    public static boolean isZero(BigDecimal value) {
        return value != null && value.signum() == 0;
    }
}
