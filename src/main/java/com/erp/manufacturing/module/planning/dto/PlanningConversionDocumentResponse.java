package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A draft document created by one conversion request. */
public record PlanningConversionDocumentResponse(
        String type,
        UUID documentId,
        String documentCode,
        String status,
        BigDecimal quantity,
        List<UUID> requirementLineIds
) {}
