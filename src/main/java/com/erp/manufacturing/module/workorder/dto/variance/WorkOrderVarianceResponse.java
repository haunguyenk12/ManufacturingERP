package com.erp.manufacturing.module.workorder.dto.variance;

import java.util.List;
import java.util.UUID;

public record WorkOrderVarianceResponse(
        UUID workOrderId,
        String workOrderNo,
        UUID productItemId,
        String productItemCode,
        String status,
        List<WorkOrderMaterialVarianceLineResponse> materialLines,
        WorkOrderOutputVarianceResponse outputVariance,
        WorkOrderWipSummaryResponse wipSummary,
        WorkOrderTimeVarianceResponse timeVariance,
        WorkOrderCostVarianceResponse costVariance
) {}
