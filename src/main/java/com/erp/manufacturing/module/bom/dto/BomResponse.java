package com.erp.manufacturing.module.bom.dto;

import java.math.BigDecimal;
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
        List<BomLineResponse> lines,
        String bomCode,
        Integer versionNumber,
        BigDecimal outputQuantity,
        String outputUomCode,
        Instant effectiveFrom,
        /** Optimistic-lock counter — send it back as {@code expectedVersion} (FE calls it lockVersion). */
        Long version
) {}
