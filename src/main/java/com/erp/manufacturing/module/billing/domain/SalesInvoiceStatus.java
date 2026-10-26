package com.erp.manufacturing.module.billing.domain;

/** G10c. POSTED and CANCELLED are final (FE Q33): no edit, no delete, no reversal. */
public enum SalesInvoiceStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
