package com.erp.manufacturing.module.workorder.dto.core;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** {@code POST /work-orders/{id}/plan} (G8a). Component warehouses may still be set here, as on PATCH. */
public record WorkOrderPlanRequest(
        @NotNull Long expectedWorkOrderVersion,
        @Valid List<ComponentWarehouseRequest> componentWarehouses
) {}
