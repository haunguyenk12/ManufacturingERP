package com.erp.manufacturing.module.workorder.dto.variance;

import java.math.BigDecimal;

public record WorkOrderWipSummaryResponse(
        BigDecimal scrapQuantity,
        BigDecimal reworkQuantity
) {}
