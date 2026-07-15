package com.erp.manufacturing.module.bom.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BomResponse(
        UUID bomId,
        UUID companyId,
        UUID parentItemId,
        String parentItemCode,
        String parentItemName,
        String revision,
        String status,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<BomLineResponse> lines
) {}
