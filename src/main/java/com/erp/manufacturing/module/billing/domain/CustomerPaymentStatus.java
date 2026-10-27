package com.erp.manufacturing.module.billing.domain;

/** G10d. POSTED and CANCELLED are final (FE Q33): no edit, no delete, no reversal. */
public enum CustomerPaymentStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
