package com.erp.manufacturing.module.routing.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Copies any routing version into a new DRAFT of the same product (G5). The code is inherited — a code
 * names the product's routing family. Absent {@code name}/{@code note} keep the source's; absent
 * {@code version} defaults to the new version number.
 */
public record RoutingCopyRequest(
        @Size(max = 40) String version,
        @Size(max = 255) String name,
        Instant effectiveFrom,
        @Size(max = 2000) String note
) {}
