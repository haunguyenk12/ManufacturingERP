package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
        Instant createdAt
) {
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
