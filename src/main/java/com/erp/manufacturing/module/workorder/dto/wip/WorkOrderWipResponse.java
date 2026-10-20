package com.erp.manufacturing.module.workorder.dto.wip;

import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.operation.WorkOrderOperationProgressResponse;

import java.util.List;
import java.util.UUID;

/**
 * Work in progress of one work order (G8d, FE R15). A section that was not asked for is null; one that was asked for
 * but holds nothing is an empty list. Quantities only — values are not tracked yet (D10).
 */
public record WorkOrderWipResponse(
        UUID workOrderId,
        String workOrderNo,
        WorkOrderStatus status,
        Long version,
        boolean inputKnown,
        boolean consumptionKnown,
        WipSection section,
        List<WipMaterialLineResponse> materials,
        List<WorkOrderOperationProgressResponse> operations,
        List<WipReworkLineResponse> rework,
        WipOutputResponse output
) {}
