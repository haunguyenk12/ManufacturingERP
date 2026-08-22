package com.erp.manufacturing.module.inventory.dto;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record ItemResponse(
        UUID itemId,
        UUID companyId,
        String code,
        String name,
        String type,
        String unit,
        boolean lotTracked,
        @Schema(hidden = true) boolean serialTracked,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
