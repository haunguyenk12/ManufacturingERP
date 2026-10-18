package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MrpRequirementLineResponse(
        UUID mrpRequirementLineId,
        UUID mrpRunId,
        UUID parentRequirementLineId,
        UUID sourceDemandId,
        UUID itemId,
        String itemSku,
        String itemName,
        /** Unit of measure of the item (spec §2.4 "Requirement"). */
        String uom,
        UUID warehouseId,
        String warehouseCode,
        Integer requirementLevel,
        BigDecimal grossRequiredQuantity,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        BigDecimal openSupplyQuantity,
        BigDecimal safetyStockQuantity,
        /**
         * Coverage still unclaimed when this line was netted (spec §2.4). Null for runs executed
         * before {@code V40}. Cannot be derived client-side from {@code availableQuantity +
         * openSupplyQuantity} — see {@code MrpRequirementLine#projectedAvailableQuantity}.
         */
        BigDecimal projectedAvailableQuantity,
        BigDecimal netRequiredQuantity,
        LocalDate dueDate,
        String requirementStatus,
        String settingSource,
        String warehouseResolutionSource,
        Integer excludedLotCount,
        String note,
        Instant createdAt,
        /** {@code SALES} (level 0 of a sales order demand), {@code COMPONENT}, or the demand type of a level-0 manual/forecast demand. */
        String sourceKind,
        /** The sales order line the root demand came from — on every level, for trace; only level 0 covers it. */
        List<UUID> sourceSalesOrderLineIds,
        List<UUID> parentRequirementLineIds,
        /** {@code BLOCKED} when the proposal is blocked (missing BOM/routing/warehouse policy), else {@code CALCULATED}. */
        String calculationState,
        UUID supplySuggestionId,
        /** {@code MAKE}/{@code BUY}; {@code null} when nothing is to be supplied. */
        String supplyType,
        String exceptionState,
        List<String> messages,
        BigDecimal convertedQuantity,
        BigDecimal remainingConvertibleQuantity,
        /** Ledger rows of this requirement — only on {@code GET .../requirements/{id}}, {@code null} on the list. */
        List<PlanningConversionResponse> conversions
) {
    public MrpRequirementLineResponse(UUID mrpRequirementLineId, UUID mrpRunId,
                                      UUID parentRequirementLineId, UUID sourceDemandId,
                                      UUID itemId, String itemSku, String itemName, String uom,
                                      UUID warehouseId, String warehouseCode, Integer requirementLevel,
                                      BigDecimal grossRequiredQuantity, BigDecimal availableQuantity,
                                      BigDecimal reservedQuantity, BigDecimal openSupplyQuantity,
                                      BigDecimal safetyStockQuantity, BigDecimal projectedAvailableQuantity,
                                      BigDecimal netRequiredQuantity, LocalDate dueDate,
                                      String requirementStatus, String settingSource, String warehouseResolutionSource,
                                      Integer excludedLotCount, String note, Instant createdAt) {
        this(mrpRequirementLineId, mrpRunId, parentRequirementLineId, sourceDemandId, itemId,
                itemSku, itemName, uom, warehouseId, warehouseCode, requirementLevel,
                grossRequiredQuantity, availableQuantity, reservedQuantity, openSupplyQuantity,
                safetyStockQuantity, projectedAvailableQuantity, netRequiredQuantity, dueDate,
                requirementStatus, settingSource, warehouseResolutionSource, excludedLotCount, note, createdAt,
                null, List.of(), parentRequirementLineId == null ? List.of() : List.of(parentRequirementLineId),
                "CALCULATED", null, null, null, List.of(), null, null, null);
    }

    public MrpRequirementLineResponse(UUID mrpRequirementLineId, UUID mrpRunId,
                                      UUID parentRequirementLineId, UUID sourceDemandId,
                                      UUID itemId, String itemSku, String itemName, String uom,
                                      UUID warehouseId, String warehouseCode, Integer requirementLevel,
                                      BigDecimal grossRequiredQuantity, BigDecimal availableQuantity,
                                      BigDecimal reservedQuantity, BigDecimal openSupplyQuantity,
                                      BigDecimal safetyStockQuantity, BigDecimal projectedAvailableQuantity,
                                      BigDecimal netRequiredQuantity, LocalDate dueDate,
                                      String requirementStatus, String settingSource, Integer excludedLotCount,
                                      String note, Instant createdAt) {
        this(mrpRequirementLineId, mrpRunId, parentRequirementLineId, sourceDemandId, itemId,
                itemSku, itemName, uom, warehouseId, warehouseCode, requirementLevel,
                grossRequiredQuantity, availableQuantity, reservedQuantity, openSupplyQuantity,
                safetyStockQuantity, projectedAvailableQuantity, netRequiredQuantity, dueDate,
                requirementStatus, settingSource, "UNRESOLVED", excludedLotCount, note, createdAt);
    }
}
