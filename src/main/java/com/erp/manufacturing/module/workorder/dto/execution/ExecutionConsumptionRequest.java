package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G8c (FE R13): material an execution actually used, from one posted issue line of the same work order. Several
 * entries for the same line are added up before the cap is checked.
 */
public record ExecutionConsumptionRequest(
        @NotNull UUID issueLineId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity
) {}
