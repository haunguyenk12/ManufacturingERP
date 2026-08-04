package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code orderNo}/{@code companyId}/{@code plantId} are deliberately absent — identity fields,
 * not something PATCH replaces (same rationale as UOM {@code code}, {@code C2-3}).
 *
 * <p>{@code customerName}/{@code orderDate}/{@code note} follow the "null = unchanged" convention
 * (cf. {@code SupplierUpdateRequest}). {@code lines == null} keeps the existing lines; a non-null
 * list is a full replace (same shape as {@link SalesOrderLineRequest} used by
 * {@code POST /sales-orders}) — every existing line is dropped and rebuilt 1..N.
 */
public record SalesOrderUpdateRequest(
        @NotNull Long expectedVersion,
        @Size(max = 255) String customerName,
        LocalDate orderDate,
        @Size(max = 2000) String note,
        List<@Valid SalesOrderLineRequest> lines
) {}
