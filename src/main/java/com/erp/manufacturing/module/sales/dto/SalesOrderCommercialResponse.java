package com.erp.manufacturing.module.sales.dto;

/**
 * G10a (D6): order-level money, present only for callers who may see sales prices. {@code pricingVersion} is
 * opaque; an invoice preview echoes it back.
 */
public record SalesOrderCommercialResponse(
        String currency,
        String pricingVersion,
        int missingPriceLineCount
) {
    public static final String VND = "VND";
}
