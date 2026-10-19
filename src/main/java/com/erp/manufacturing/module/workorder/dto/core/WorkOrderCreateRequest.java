package com.erp.manufacturing.module.workorder.dto.core;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkOrderCreateRequest(
        @NotBlank @Size(max = 100) String workOrderNo,
        @NotNull UUID productItemId,
        @NotNull UUID outputWarehouseId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal plannedQuantity,
        Instant plannedStartAt,
        Instant plannedEndAt,
        @Size(max = 2000) String notes,
        /** G8a (D2): optional; a component item left out keeps reserving across the whole plant. */
        @Valid List<ComponentWarehouseRequest> componentWarehouses
) {

    /** Shape before G8a — the planning conversion builds work orders without component warehouses. */
    public WorkOrderCreateRequest(String workOrderNo, UUID productItemId, UUID outputWarehouseId,
                                  BigDecimal plannedQuantity, Instant plannedStartAt, Instant plannedEndAt,
                                  String notes) {
        this(workOrderNo, productItemId, outputWarehouseId, plannedQuantity, plannedStartAt, plannedEndAt,
                notes, null);
    }
}
