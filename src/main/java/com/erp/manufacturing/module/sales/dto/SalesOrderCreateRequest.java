package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Legacy create (client-chosen {@code orderNo}). G10a: {@code customerId} is optional and additive — confirming
 * now requires a master customer (D4), so a legacy client either sends it here or links one through
 * {@code PATCH /{id}/draft}. When sent, the master name replaces {@code customerName}.
 */
public record SalesOrderCreateRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        @NotBlank @Size(max = 100) String orderNo,
        @NotBlank @Size(max = 255) String customerName,
        @NotNull LocalDate orderDate,
        @Size(max = 2000) String note,
        @NotEmpty List<@Valid SalesOrderLineRequest> lines,
        UUID customerId
) {
    /** Shape before G10a: no customer link. */
    public SalesOrderCreateRequest(UUID companyId, UUID plantId, String orderNo, String customerName,
                                   LocalDate orderDate, String note, List<SalesOrderLineRequest> lines) {
        this(companyId, plantId, orderNo, customerName, orderDate, note, lines, null);
    }
}
