package com.erp.manufacturing.module.routing.dto;

import java.math.BigDecimal;
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
        List<RoutingOperationResponse> operations,
        String name,
        Integer versionNumber,
        Instant effectiveFrom,
        /** Plant of the operations' work centers ({@code B_wc2}); {@code null} for a legacy routing without any. */
        UUID plantId,
        BigDecimal totalSetupMinutes,
        BigDecimal totalRunMinutesPerUnit,
        /** Optimistic-lock counter — send it back as {@code expectedVersion}. {@link #version} is the business label. */
        Long lockVersion
) {}
