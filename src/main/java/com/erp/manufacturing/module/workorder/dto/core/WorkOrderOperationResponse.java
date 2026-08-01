package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkOrderOperationResponse(
        UUID workOrderOperationId,
        UUID sourceRoutingOperationId,
        Integer sequence,
        String name,
        String workCenterCode,
        BigDecimal setupMinutes,
        BigDecimal runMinutesPerUnit
) {}
