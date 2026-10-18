package com.erp.manufacturing.module.planning.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response of a conversion request. Echo {@code conversionVersion} and {@code sourceVersion} on the
 * next request. A replay of the same {@code Idempotency-Key} returns the same documents.
 */
public record PlanningConversionResult(
        UUID mrpRunId,
        Integer conversionVersion,
        String sourceVersion,
        List<PlanningConversionDocumentResponse> documents,
        List<PlanningConversionResponse> conversions
) {}
