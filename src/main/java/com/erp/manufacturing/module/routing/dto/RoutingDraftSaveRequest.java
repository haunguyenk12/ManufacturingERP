package com.erp.manufacturing.module.routing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Atomic save of a routing draft and its whole operation graph (G5). {@code routingId} absent ⇒ a new
 * DRAFT version of {@code itemId} (only one open draft per product, FE Q18); present ⇒ full
 * replacement of that DRAFT with {@code expectedVersion}. {@code code} and {@code itemId} never change
 * on a re-save. Routing stays company-scoped (D5); the plant follows from the work centers.
 */
public record RoutingDraftSaveRequest(
        UUID routingId,
        Long expectedVersion,
        @NotBlank @Size(max = 100) String code,
        @Size(max = 255) String name,
        @NotNull UUID itemId,
        @Size(max = 40) String version,
        Instant effectiveFrom,
        @Size(max = 2000) String note,
        @NotEmpty List<@Valid RoutingDraftOperationRequest> operations
) {}
