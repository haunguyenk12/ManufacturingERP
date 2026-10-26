package com.erp.manufacturing.module.billing.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A price check, nothing saved or held. Send {@code billingSnapshotVersion} back when saving these lines. */
public record SalesInvoicePreviewResponse(
        UUID salesOrderId,
        String currency,
        String pricingVersion,
        String billingSnapshotVersion,
        BigDecimal amount,
        List<SalesInvoicePreviewLineResponse> lines
) {}
