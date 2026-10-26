package com.erp.manufacturing.module.delivery.domain;

/** G10b. POSTED means "dispatched from the warehouse" — not proof of delivery (FE Q39). POSTED and CANCELLED are final. */
public enum DeliveryStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
