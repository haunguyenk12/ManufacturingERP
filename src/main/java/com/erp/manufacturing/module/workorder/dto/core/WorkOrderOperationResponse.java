package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkOrderOperationResponse(
        UUID workOrderOperationId,
        UUID sourceRoutingOperationId,
        Integer sequence,
        String name,
        String workCenterCode,
        UUID workCenterId,
        BigDecimal setupMinutes,
        BigDecimal runMinutesPerUnit,
        Instant plannedStartAt,
        Instant plannedEndAt,
        Long version
) {}
