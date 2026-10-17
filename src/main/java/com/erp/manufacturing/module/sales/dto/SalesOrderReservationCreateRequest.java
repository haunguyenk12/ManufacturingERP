package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Hold stock for one line (G6a). Without {@code lotId} a lot-tracked item is spread FEFO over the
 * eligible lots of the warehouse (FE Q25). {@code expectedVersion} is the sales order version: a
 * blind retry of an already-applied hold becomes a 409 instead of a second hold.
 */
public record SalesOrderReservationCreateRequest(
        @NotNull Long expectedVersion,
        @NotNull UUID salesOrderLineId,
        @NotNull UUID warehouseId,
        UUID lotId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity
) {}
