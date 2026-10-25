package com.erp.manufacturing.module.sales.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * G10 (FE Q25): what every GĐ10 command answers — identity, type and version, never the document's money. The
 * client re-reads the detail it is allowed to see. {@code pricingVersion} only on {@code SALES_PRICE}.
 */
public record CommercialCommandResult(
        UUID documentId,
        String type,
        Long version,
        @JsonInclude(JsonInclude.Include.NON_NULL) String pricingVersion
) {
    public static final String SALES_PRICE = "SALES_PRICE";

    public static CommercialCommandResult of(UUID documentId, String type, Long version) {
        return new CommercialCommandResult(documentId, type, version, null);
    }
}
