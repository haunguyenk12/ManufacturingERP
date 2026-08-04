package com.erp.manufacturing.module.workcenter.dto;

import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code code} and {@code plantId} are deliberately absent — both are immutable after creation
 * (NEXT_PHASE_PLAN.md C2-6 §Phần A "Thiết kế đã chốt" #3). Every field here is optional: a
 * {@code null} value means "leave unchanged", following {@code SupplierUpdateRequest}.
 */
public record WorkCenterUpdateRequest(
        @Size(max = 255) String name,
        String description,
        CapacityUnitType capacityUnitType,
        @Positive Integer capacityUnits
) {}
