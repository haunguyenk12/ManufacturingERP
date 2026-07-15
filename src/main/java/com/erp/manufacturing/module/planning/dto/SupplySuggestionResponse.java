package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SupplySuggestionResponse(
        UUID supplySuggestionId,
        UUID mrpRunId,
        UUID requirementLineId,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        UUID warehouseId,
        String warehouseCode,
        UUID itemId,
        String itemCode,
        String itemName,
        String suggestionType,
        BigDecimal suggestedQuantity,
        LocalDate neededByDate,
        LocalDate suggestedOrderDate,
        String status,
        String decisionNote,
        String convertedReferenceType,
        UUID convertedReferenceId,
        Instant createdAt,
        Instant updatedAt
) {}
