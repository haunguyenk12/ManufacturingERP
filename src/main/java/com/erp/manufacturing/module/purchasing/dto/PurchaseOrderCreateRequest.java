package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderCreateRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        @NotNull UUID warehouseId,
        @NotNull UUID supplierId,
        @NotBlank @Size(max = 100) String purchaseOrderNo,
        @NotNull LocalDate orderDate,
        @NotNull LocalDate expectedDate,
        UUID sourceRequisitionId,
        @Size(max = 2000) String note,
        @NotEmpty List<@Valid PurchaseOrderLineRequest> lines
) {}
