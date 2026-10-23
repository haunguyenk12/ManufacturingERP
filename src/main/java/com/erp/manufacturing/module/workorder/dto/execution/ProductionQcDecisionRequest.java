package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * G9c (FE §3.3, R09): one QC decision on part of what an APPROVED receipt still has pending. Both quantities
 * are required (0 allowed), together more than 0; a rejection needs a reason of 5–2000 characters.
 */
public record ProductionQcDecisionRequest(
        @NotNull Long expectedVersion,
        @NotNull Long expectedWorkOrderVersion,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal acceptedQuantity,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal rejectedQuantity,
        @Size(max = 2000) String reason
) {}
