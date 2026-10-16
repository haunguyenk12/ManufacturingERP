package com.erp.manufacturing.module.routing.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One operation of a routing draft graph (G5). {@code clientKey} names the row inside this request so
 * {@code predecessorClientKeys} can point at rows that have no server id yet; it is never stored.
 * {@code routingOperationId} keeps an existing operation's id on a re-save.
 */
public record RoutingDraftOperationRequest(
        UUID routingOperationId,
        @NotBlank @Size(max = 100) String clientKey,
        @NotNull @Positive Integer sequence,
        @NotBlank @Size(max = 255) String name,
        @NotNull UUID workCenterId,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal setupMinutes,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal runMinutesPerUnit,
        List<@NotBlank String> predecessorClientKeys
) {}
