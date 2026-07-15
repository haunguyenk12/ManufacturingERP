package com.erp.manufacturing.module.workorder.dto.core;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkOrderUpdateRequest(
        UUID outputWarehouseId,
        @Positive BigDecimal plannedQuantity,
        Instant plannedStartAt,
        Instant plannedEndAt,
        @Size(max = 2000) String notes
) {}
