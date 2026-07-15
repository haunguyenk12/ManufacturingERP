package com.erp.manufacturing.module.workorder.dto.variance;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkOrderMaterialVarianceLineResponse(
        UUID componentLineId,
        UUID componentItemId,
        String componentItemCode,
        String componentItemName,
        BigDecimal plannedQuantity,
        BigDecimal actualIssuedQuantity,
        BigDecimal varianceQuantity,
        String status
) {}
