package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /sales-orders/v1/drafts} (G6a): a new DRAFT whose {@code orderNo} the server generates
 * and whose customer comes from the customer master. The legacy {@code POST /v1} (client-chosen
 * {@code orderNo}, free-text customer) keeps working unchanged.
 */
public record SalesOrderDraftCreateRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        @NotNull UUID customerId,
        @Size(max = 255) String customerReference,
        @NotNull LocalDate orderDate,
        @Size(max = 2000) String note,
        @NotNull @Size(min = 1, max = 500) List<@Valid @NotNull SalesOrderDraftLineRequest> lines
) {}
