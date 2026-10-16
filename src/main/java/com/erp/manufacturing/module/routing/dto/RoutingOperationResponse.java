package com.erp.manufacturing.module.routing.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RoutingOperationResponse(
        UUID routingOperationId,
        Integer sequence,
        String name,
        UUID workCenterId,
        String workCenterCode,
        BigDecimal setupMinutes,
        BigDecimal runMinutesPerUnit,
        String workCenterName,
        List<UUID> predecessorOperationIds
) {}
