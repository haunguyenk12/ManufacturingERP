package com.erp.manufacturing.module.workorder.dto.variance;

import java.math.BigDecimal;

public record WorkOrderOutputVarianceResponse(
        BigDecimal plannedQuantity,
        BigDecimal actualOutputQuantity,
        BigDecimal varianceQuantity
) {}
