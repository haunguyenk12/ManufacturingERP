package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record MaterialReservationCreateRequest(
        @NotNull UUID componentLineId,
        @NotNull UUID warehouseId,
        UUID lotId,
        @NotNull @Positive BigDecimal quantity
) {}
