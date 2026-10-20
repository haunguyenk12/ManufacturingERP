package com.erp.manufacturing.module.workorder.dto.materialreturn;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G8d (FE R16): give back part of one posted issue line of the work order. Item, warehouse and lot come from that
 * line — the client cannot choose another source. Several entries for the same line are added up before the cap.
 */
public record MaterialReturnLineRequest(
        @NotNull UUID sourceIssueLineId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity
) {}
