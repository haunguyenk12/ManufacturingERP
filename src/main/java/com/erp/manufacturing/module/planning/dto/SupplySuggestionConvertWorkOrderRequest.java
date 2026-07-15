package com.erp.manufacturing.module.planning.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record SupplySuggestionConvertWorkOrderRequest(
        @Size(max = 100) String workOrderNo,
        UUID outputWarehouseId,
        Instant plannedStartAt,
        Instant plannedEndAt,
        @Size(max = 2000) String notes
) {}
