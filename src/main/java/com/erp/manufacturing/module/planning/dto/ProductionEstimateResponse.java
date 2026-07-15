package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductionEstimateResponse(
        UUID productItemId,
        String productItemCode,
        String productItemName,
        String scopeType,
        UUID scopeId,
        UUID companyId,
        BigDecimal targetQuantity,
        BigDecimal maxBuildableQuantity,
        List<ProductionEstimateLineResponse> lines,
        ProductionEstimateSummaryResponse summary
) {}
