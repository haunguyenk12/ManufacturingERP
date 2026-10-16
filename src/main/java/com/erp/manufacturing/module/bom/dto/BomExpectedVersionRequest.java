package com.erp.manufacturing.module.bom.dto;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /boms/{id}/deactivate} (G5) — required, unlike the optional one on activate. */
public record BomExpectedVersionRequest(@NotNull Long expectedVersion) {}
