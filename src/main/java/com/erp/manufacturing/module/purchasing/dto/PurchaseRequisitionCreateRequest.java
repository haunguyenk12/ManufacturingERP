package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseRequisitionCreateRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        @NotNull UUID warehouseId,
        @NotBlank @Size(max = 100) String requisitionNo,
        @NotNull LocalDate neededByDate,
        @Size(max = 80) String sourceType,
        UUID sourceId,
        @NotEmpty List<@Valid PurchaseRequisitionLineRequest> lines
) {}
