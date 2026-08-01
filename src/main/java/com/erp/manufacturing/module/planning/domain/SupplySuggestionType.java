package com.erp.manufacturing.module.planning.domain;

/**
 * How a shortage is meant to be covered. The constant names are the persisted values (V18 check
 * constraint) and stay as they are; {@link #supplyType()} is the name the API uses (spec §2.4).
 */
public enum SupplySuggestionType {
    WORK_ORDER("MAKE"),
    PURCHASE_REQUISITION("BUY");

    private final String supplyType;

    SupplySuggestionType(String supplyType) {
        this.supplyType = supplyType;
    }

    public String supplyType() {
        return supplyType;
    }
}
