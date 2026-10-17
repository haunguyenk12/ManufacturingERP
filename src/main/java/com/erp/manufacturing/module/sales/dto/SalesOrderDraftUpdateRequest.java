package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code PATCH /sales-orders/v1/{id}/draft} (G6a): full replacement of a DRAFT — header and every line
 * in one transaction. Unlike the legacy {@code PATCH /{id}} nothing here means "keep": every field is
 * the new state. Line ids are reissued, as in the legacy full replace.
 */
public record SalesOrderDraftUpdateRequest(
        @NotNull Long expectedVersion,
        @NotNull UUID customerId,
        @Size(max = 255) String customerReference,
        @NotNull LocalDate orderDate,
        @Size(max = 2000) String note,
        @NotNull @Size(min = 1, max = 500) List<@Valid @NotNull SalesOrderDraftLineRequest> lines
) {}
