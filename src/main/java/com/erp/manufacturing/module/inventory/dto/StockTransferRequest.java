package com.erp.manufacturing.module.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /v1/inventory/transfers} (G4-2). Both warehouses must be ACTIVE, different and in the
 * same ACTIVE plant; 1–100 lines; the same item+lot may appear on one line only (D8).
 */
public record StockTransferRequest(
        @NotNull UUID fromWarehouseId,
        @NotNull UUID toWarehouseId,
        @NotBlank @Size(max = 1000) String reason,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull StockTransferLineRequest> lines
) {}
