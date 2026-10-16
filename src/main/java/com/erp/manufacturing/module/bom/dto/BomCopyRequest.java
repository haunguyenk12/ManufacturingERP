package com.erp.manufacturing.module.bom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Copies any version into a new DRAFT of the same product (G5). {@code description} absent keeps the
 * source's; {@code revision} absent defaults to the new version number.
 */
public record BomCopyRequest(
        @NotBlank @Size(max = 100) String bomCode,
        @Size(max = 40) String revision,
        Instant effectiveFrom,
        @Size(max = 1000) String description
) {}
