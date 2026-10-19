package com.erp.manufacturing.module.workorder.dto.core;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code PATCH /work-orders/{id}} on a {@code DRAFT}. {@code null} keeps a field. A work order linked to
 * a planning source refuses a new {@code plannedQuantity} or {@code outputWarehouseId} with
 * {@code SOURCE_LOCKED} (G8a, FE Q7).
 */
public record WorkOrderUpdateRequest(
        UUID outputWarehouseId,
        @Positive @Digits(integer = 13, fraction = 6) BigDecimal plannedQuantity,
        Instant plannedStartAt,
        Instant plannedEndAt,
        @Size(max = 2000) String notes,
        /** G8 (D5): the version last read; required. */
        @NotNull Long expectedVersion,
        /** G8a (D2): replaces the warehouse of each listed component item; items left out keep theirs. */
        @Valid List<ComponentWarehouseRequest> componentWarehouses
) {}
