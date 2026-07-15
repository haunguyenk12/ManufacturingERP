package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PurchaseRequisitionFromSuggestionRequest(
        @NotBlank @Size(max = 100) String requisitionNo,
        UUID warehouseId,
        UUID supplierId,
        @Size(max = 2000) String note
) {}
