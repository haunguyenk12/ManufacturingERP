package com.erp.manufacturing.module.workorder.dto.operation;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * G8c (FE R10): confirm input of an operation. On the first operation it comes from the plan; on a later one it is
 * a transfer of the predecessor's good output — the server resolves the predecessor, the client never names it.
 */
public record OperationInputRequest(
        @NotNull Long expectedWorkOrderVersion,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity
) {}
