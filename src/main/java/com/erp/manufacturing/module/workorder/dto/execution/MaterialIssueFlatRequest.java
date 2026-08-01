package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Flat single-line material issue (spec §4.1). The work order and component are derived from the
 * reservation, which is what the shop-floor screen already has in hand.
 */
public record MaterialIssueFlatRequest(
        @NotNull UUID workOrderId,
        @NotNull UUID reservationId,
        @NotNull @Positive BigDecimal quantity,
        @Size(max = 1000) String reason
) {}
