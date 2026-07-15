package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record PurchaseRequisitionConvertToOrderRequest(
        @NotBlank @Size(max = 100) String purchaseOrderNo,
        UUID supplierId,
        @NotNull LocalDate orderDate,
        @NotNull LocalDate expectedDate,
        @Size(max = 2000) String note
) {}
