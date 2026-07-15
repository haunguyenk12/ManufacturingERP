package com.erp.manufacturing.module.workorder.dto.core;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkOrderCreateRequest(
        @NotBlank @Size(max = 100) String workOrderNo,
        @NotNull UUID productItemId,
        @NotNull UUID outputWarehouseId,
        @NotNull @Positive BigDecimal plannedQuantity,
        Instant plannedStartAt,
        Instant plannedEndAt,
        @Size(max = 2000) String notes
) {}
