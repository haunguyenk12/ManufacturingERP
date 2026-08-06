package com.erp.manufacturing.module.inventory.dto;

import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
        UUID itemId,
        UUID companyId,
        String code,
        String name,
        String type,
        String unit,
        boolean lotTracked,
        boolean serialTracked,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
