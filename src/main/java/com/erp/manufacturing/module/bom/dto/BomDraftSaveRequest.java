package com.erp.manufacturing.module.bom.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One atomic save of a BOM draft — header and every line in one transaction (G5, D4).
 *
 * <p>{@code bomId} absent ⇒ a new DRAFT version of {@code parentItemId}; present ⇒ full replacement of
 * that DRAFT, which then also needs {@code expectedVersion}. Being a PUT, an absent optional field
 * clears it ({@code description}, {@code effectiveFrom}); {@code revision} is the exception — absent
 * keeps the current label, or on create defaults to the version number.
 */
public record BomDraftSaveRequest(
        UUID bomId,
        Long expectedVersion,
        @NotBlank @Size(max = 100) String bomCode,
        @NotNull UUID parentItemId,
        @Size(max = 40) String revision,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal outputQuantity,
        Instant effectiveFrom,
        @Size(max = 1000) String description,
        @NotNull List<@Valid BomDraftLineRequest> lines
) {}
