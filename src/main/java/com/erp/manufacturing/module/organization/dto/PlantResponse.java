package com.erp.manufacturing.module.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record PlantResponse(
        UUID plantId,
        UUID companyId,
        String code,
        String name,
        String timezone,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
