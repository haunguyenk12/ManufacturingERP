package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /v1/purchase-orders/drafts} (G7a, D2): a DRAFT whose number the server issues
 * ({@code PO-000001}…). The legacy {@code POST /v1/purchase-orders} with a client-chosen number keeps
 * working unchanged.
 */
public record PurchaseOrderDraftCreateRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        @NotNull UUID supplierId,
        @NotNull UUID warehouseId,
        @NotNull LocalDate orderDate,
        @Size(max = 200) String reference,
        @Size(max = 2000) String notes,
        @NotNull @Size(min = 1, max = 500) List<@Valid @NotNull PurchaseOrderDraftLineRequest> lines
) {}
