package com.erp.manufacturing.module.organization.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Admin-defined access scope category. */
public enum ScopeType {
    GLOBAL,
    COMPANY,
    PLANT,
    WAREHOUSE_GROUP,
    CUSTOM;

    @JsonCreator
    public static ScopeType fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Scope type is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("WAREHOUSE")) {
            return WAREHOUSE_GROUP;
        }
        return ScopeType.valueOf(normalized);
    }

    @JsonValue
    public String toWireValue() {
        return this == WAREHOUSE_GROUP ? "WAREHOUSE" : name();
    }
}
