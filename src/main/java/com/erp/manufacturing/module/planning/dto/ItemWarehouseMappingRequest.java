package com.erp.manufacturing.module.planning.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Netting warehouse of one item for one commercial run (G6b, FE Q9). {@code outputWarehouseId} may
 * be omitted; when given it must equal {@code demandWarehouseId} (FE Q16 — no planned transfer yet).
 */
public record ItemWarehouseMappingRequest(
        @NotNull UUID itemId,
        @NotNull UUID demandWarehouseId,
        UUID outputWarehouseId
) {}
