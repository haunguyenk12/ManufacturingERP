package com.erp.manufacturing.module.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code replaceDefaults} (G2-3) decides what happens when a default role this request claims is held
 * by another warehouse of the same item and plant:
 * <ul>
 *   <li>{@code null} (omitted) — move the role here silently, the behaviour every existing client
 *       relies on;</li>
 *   <li>{@code false} — refuse with {@code DEFAULT_CONFLICT} (409) and write nothing, so the UI can ask
 *       the user to confirm;</li>
 *   <li>{@code true} — move the role here (the confirmed form).</li>
 * </ul>
 * Nullable on purpose: FE proposed defaulting to {@code false}, which would have started failing
 * requests that succeed today (NEXT_PHASE_PLAN.md 9.2 D2).
 */
public record ItemWarehouseSettingRequest(
        @NotNull UUID itemId,
        @NotNull UUID warehouseId,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal safetyStock,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal reorderPoint,
        @NotNull @Min(0) Integer leadTimeDays,
        boolean defaultSupply,
        boolean defaultOutput,
        Boolean replaceDefaults
) {
    public ItemWarehouseSettingRequest(UUID itemId, UUID warehouseId, BigDecimal safetyStock,
                                       BigDecimal reorderPoint, Integer leadTimeDays) {
        this(itemId, warehouseId, safetyStock, reorderPoint, leadTimeDays, false, false, null);
    }

    public ItemWarehouseSettingRequest(UUID itemId, UUID warehouseId, BigDecimal safetyStock,
                                       BigDecimal reorderPoint, Integer leadTimeDays,
                                       boolean defaultSupply, boolean defaultOutput) {
        this(itemId, warehouseId, safetyStock, reorderPoint, leadTimeDays, defaultSupply, defaultOutput, null);
    }
}
