package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSettingStatus;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAvailabilityService {

    private final StockBalanceRepository stockBalanceRepository;
    private final ItemWarehouseSettingRepository itemWarehouseSettingRepository;

    /**
     * Tìm StockBalance theo item + warehouse + lot (nullable).
     * Dùng cho nghiệp vụ material issue/reservation khi cần kiểm tra
     * và thay đổi số dư tồn kho của một lot cụ thể.
     */
    @Transactional(readOnly = true)
    public StockBalance getStockBalanceForIssue(Item item, UUID warehouseId, UUID lotId) {
        return (lotId == null
                ? stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(
                        item.getItemId(), warehouseId)
                : stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                        item.getItemId(), warehouseId, lotId))
                .orElseThrow(() -> ExceptionFactory.businessRule(BusinessErrorCode.INSUFFICIENT_STOCK,
                        "No stock balance exists for this item, warehouse, and lot"));
    }

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getAvailableQuantities(Collection<UUID> itemIds, Collection<UUID> warehouseIds) {
        if (itemIds == null || itemIds.isEmpty() || warehouseIds == null || warehouseIds.isEmpty()) {
            return Map.of();
        }

        return stockBalanceRepository.aggregateAvailableQuantities(itemIds, warehouseIds, LotStatus.AVAILABLE).stream()
                .collect(Collectors.toMap(
                        StockAvailabilityProjection::getItemId,
                        StockAvailabilityProjection::getQuantity));
    }

    @Transactional(readOnly = true)
    public Map<ItemWarehouseAvailabilityKey, BigDecimal> getAvailableQuantitiesByWarehouse(
            Collection<UUID> itemIds, Collection<UUID> warehouseIds) {
        if (itemIds == null || itemIds.isEmpty() || warehouseIds == null || warehouseIds.isEmpty()) {
            return Map.of();
        }

        return stockBalanceRepository.aggregateAvailableQuantitiesByWarehouse(itemIds, warehouseIds, LotStatus.AVAILABLE)
                .stream()
                .collect(Collectors.toMap(
                        projection -> new ItemWarehouseAvailabilityKey(projection.getItemId(), projection.getWarehouseId()),
                        StockAvailabilityByWarehouseProjection::getQuantity));
    }

    @Transactional(readOnly = true)
    public Map<UUID, PlanningInventoryQuantity> getPlanningQuantities(
            Collection<UUID> itemIds, Collection<UUID> warehouseIds) {
        if (itemIds == null || itemIds.isEmpty() || warehouseIds == null || warehouseIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PlanningInventoryQuantity> quantities = new HashMap<>();
        stockBalanceRepository.aggregatePlanningQuantities(itemIds, warehouseIds, LotStatus.AVAILABLE)
                .forEach(projection -> quantities.put(projection.getItemId(), new PlanningInventoryQuantity(
                        defaultZero(projection.getOnHandQuantity()),
                        defaultZero(projection.getReservedQuantity()),
                        defaultZero(projection.getAvailableQuantity()),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0)));

        itemWarehouseSettingRepository.aggregatePlanningSettings(
                        itemIds, warehouseIds, ItemWarehouseSettingStatus.ACTIVE)
                .forEach(projection -> {
                    PlanningInventoryQuantity current = quantities.getOrDefault(
                            projection.getItemId(), PlanningInventoryQuantity.empty());
                    quantities.put(projection.getItemId(), current.withSettings(
                            defaultZero(projection.getSafetyStockQuantity()),
                            defaultZero(projection.getReorderPointQuantity()),
                            projection.getLeadTimeDays() == null ? 0 : projection.getLeadTimeDays()));
                });

        return quantities;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record ItemWarehouseAvailabilityKey(UUID itemId, UUID warehouseId) {}

    public record PlanningInventoryQuantity(
            BigDecimal onHandQuantity,
            BigDecimal reservedQuantity,
            BigDecimal availableQuantity,
            BigDecimal safetyStockQuantity,
            BigDecimal reorderPointQuantity,
            int leadTimeDays
    ) {
        public static PlanningInventoryQuantity empty() {
            return new PlanningInventoryQuantity(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0);
        }

        public PlanningInventoryQuantity withSettings(BigDecimal safetyStockQuantity,
                                                      BigDecimal reorderPointQuantity,
                                                      int leadTimeDays) {
            return new PlanningInventoryQuantity(
                    onHandQuantity,
                    reservedQuantity,
                    availableQuantity,
                    safetyStockQuantity,
                    reorderPointQuantity,
                    leadTimeDays);
        }
    }
}
