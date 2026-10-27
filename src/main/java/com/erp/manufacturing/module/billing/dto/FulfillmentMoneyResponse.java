package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;

/** The order's receivable: POSTED invoices, POSTED payments allocated to them, and the difference. Invoice authority only. */
public record FulfillmentMoneyResponse(String currency, BigDecimal invoicedAmount, BigDecimal paidAmount,
                                       BigDecimal outstandingAmount) {}
