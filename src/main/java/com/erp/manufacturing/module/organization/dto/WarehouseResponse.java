package com.erp.manufacturing.module.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID warehouseId,
        UUID plantId,
        String code,
        String name,
        String type,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
