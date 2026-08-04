package com.erp.manufacturing.module.uom.dto;

import java.time.Instant;
import java.util.UUID;

public record UomResponse(
        UUID uomId,
        String code,
        String name,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
