package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;

/** The line's locked price ({@code null} while unknown) and what POSTED invoices billed on it. */
public record FulfillmentLineMoneyResponse(BigDecimal unitPrice, BigDecimal invoicedAmount) {}
