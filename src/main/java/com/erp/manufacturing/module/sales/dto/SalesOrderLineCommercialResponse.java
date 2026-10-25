package com.erp.manufacturing.module.sales.dto;

import java.math.BigDecimal;

/**
 * G10a (D6): the money of one line, present on the response only for callers who may see sales prices. Absent
 * (the whole object) means "not allowed to see"; present with {@code unitPrice: null} means "no price yet".
 */
public record SalesOrderLineCommercialResponse(
        BigDecimal unitPrice,
        String currency,
        boolean priceKnown
) {}
