package com.erp.manufacturing.module.routing.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoutingResponse(
        UUID routingId,
        UUID companyId,
        UUID itemId,
        String itemSku,
        String itemName,
        String code,
        String version,
        String status,
        String note,
        Instant createdAt,
        Instant updatedAt,
        List<RoutingOperationResponse> operations
) {}
