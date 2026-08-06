package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record MaterialIssueLineRequest(
        @NotNull UUID componentLineId,
        UUID reservationId,
        @NotNull UUID warehouseId,
        UUID lotId,
        @Size(max = 120) String lotNumber,
        UUID serialId,
        @NotNull @Positive BigDecimal quantity,
        @Size(max = 1000) String reason,
        /** Required when quantity exceeds the component's remaining BOM requirement. */
        @Size(max = 1000) String overrideReason
) {}
