package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.InventoryAlertLineResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardShortageSummaryResponse;
import com.erp.manufacturing.module.inventory.dto.StockMovementResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAlertService {

    private static final int DASHBOARD_ALERT_LIMIT = 10;
    private static final int RECENT_MOVEMENT_LIMIT = 10;

    private final ItemWarehouseSettingRepository settingRepository;
    private final InventoryAvailabilityService availabilityService;
    private final OrganizationLookupService organizationLookupService;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', #scopeType.name(), #scopeId)")
    public List<InventoryAlertLineResponse> listAlerts(ScopeResourceType scopeType,
                                                       UUID scopeId,
                                                       InventoryAlertStatus status) {
        OrganizationScopeResolution scope = organizationLookupService.resolveScope(scopeType, scopeId);
        return buildAlertLines(scope).stream()
                .filter(line -> status == null || line.status().equals(status.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', #scopeType.name(), #scopeId)")
    public InventoryDashboardResponse getDashboard(ScopeResourceType scopeType, UUID scopeId) {
        OrganizationScopeResolution scope = organizationLookupService.resolveScope(scopeType, scopeId);
        List<InventoryAlertLineResponse> lines = buildAlertLines(scope);
        List<InventoryAlertLineResponse> actionableLines = lines.stream()
                .filter(line -> !InventoryAlertStatus.OK.name().equals(line.status()))
                .limit(DASHBOARD_ALERT_LIMIT)
                .toList();

        List<StockMovementResponse> recentMovements = scope.warehouseIds().isEmpty()
                ? List.of()
                : stockMovementRepository.findRecentByWarehouseIds(
                        scope.warehouseIds(), PageRequest.of(0, RECENT_MOVEMENT_LIMIT)).stream()
                .map(mapper::toResponse)
                .toList();

        return new InventoryDashboardResponse(
                scope.scopeType().name(),
                scope.scopeId(),
                scope.companyId(),
                Math.toIntExact(lines.stream().map(InventoryAlertLineResponse::itemId).distinct().count()),
                scope.warehouseIds().size(),
                countByStatus(lines, InventoryAlertStatus.OK),
                countByStatus(lines, InventoryAlertStatus.LOW_STOCK),
                countByStatus(lines, InventoryAlertStatus.REORDER_NEEDED),
                buildShortageSummary(lines),
                actionableLines,
                recentMovements);
    }

    private List<InventoryAlertLineResponse> buildAlertLines(OrganizationScopeResolution scope) {
        if (scope.warehouseIds().isEmpty()) {
            return List.of();
        }

        List<ItemWarehouseSetting> settings = settingRepository.findByWarehouseWarehouseIdInAndStatus(
                scope.warehouseIds(), ItemWarehouseSettingStatus.ACTIVE);
        if (settings.isEmpty()) {
            return List.of();
        }

        Set<UUID> itemIds = settings.stream()
                .map(setting -> setting.getItem().getItemId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<InventoryAvailabilityService.ItemWarehouseAvailabilityKey, BigDecimal> availableByItemWarehouse =
                availabilityService.getAvailableQuantitiesByWarehouse(itemIds, scope.warehouseIds());

        return settings.stream()
                .map(setting -> toAlertLine(setting, availableByItemWarehouse.getOrDefault(
                        new InventoryAvailabilityService.ItemWarehouseAvailabilityKey(
                                setting.getItem().getItemId(),
                                setting.getWarehouse().getWarehouseId()),
                        BigDecimal.ZERO)))
                .sorted(alertComparator())
                .toList();
    }

    private InventoryAlertLineResponse toAlertLine(ItemWarehouseSetting setting, BigDecimal availableQuantity) {
        InventoryAlertStatus status = resolveStatus(setting, availableQuantity);
        return new InventoryAlertLineResponse(
                setting.getSettingId(),
                setting.getItem().getItemId(),
                setting.getItem().getCode(),
                setting.getItem().getName(),
                setting.getWarehouse().getWarehouseId(),
                setting.getWarehouse().getCode(),
                setting.getWarehouse().getName(),
                setting.getSafetyStock(),
                setting.getReorderPoint(),
                setting.getLeadTimeDays(),
                availableQuantity,
                status.name());
    }

    private InventoryAlertStatus resolveStatus(ItemWarehouseSetting setting, BigDecimal availableQuantity) {
        if (availableQuantity.compareTo(setting.getReorderPoint()) <= 0) {
            return InventoryAlertStatus.REORDER_NEEDED;
        }
        if (availableQuantity.compareTo(setting.getSafetyStock()) < 0) {
            return InventoryAlertStatus.LOW_STOCK;
        }
        return InventoryAlertStatus.OK;
    }

    private long countByStatus(List<InventoryAlertLineResponse> lines, InventoryAlertStatus status) {
        return lines.stream()
                .filter(line -> line.status().equals(status.name()))
                .count();
    }

    private InventoryDashboardShortageSummaryResponse buildShortageSummary(List<InventoryAlertLineResponse> lines) {
        List<InventoryAlertLineResponse> actionableLines = lines.stream()
                .filter(line -> !InventoryAlertStatus.OK.name().equals(line.status()))
                .toList();
        BigDecimal safetyStockGap = lines.stream()
                .map(line -> positiveDifference(line.safetyStock(), line.availableQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reorderPointGap = lines.stream()
                .map(line -> positiveDifference(line.reorderPoint(), line.availableQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new InventoryDashboardShortageSummaryResponse(
                actionableLines.size(),
                Math.toIntExact(countByStatus(lines, InventoryAlertStatus.LOW_STOCK)),
                Math.toIntExact(countByStatus(lines, InventoryAlertStatus.REORDER_NEEDED)),
                safetyStockGap,
                reorderPointGap);
    }

    private BigDecimal positiveDifference(BigDecimal threshold, BigDecimal availableQuantity) {
        BigDecimal difference = threshold.subtract(availableQuantity);
        return difference.compareTo(BigDecimal.ZERO) > 0 ? difference : BigDecimal.ZERO;
    }

    private Comparator<InventoryAlertLineResponse> alertComparator() {
        return Comparator
                .comparingInt((InventoryAlertLineResponse line) -> statusRank(line.status()))
                .thenComparing(InventoryAlertLineResponse::itemCode)
                .thenComparing(InventoryAlertLineResponse::warehouseCode);
    }

    private int statusRank(String status) {
        if (InventoryAlertStatus.REORDER_NEEDED.name().equals(status)) {
            return 0;
        }
        if (InventoryAlertStatus.LOW_STOCK.name().equals(status)) {
            return 1;
        }
        return 2;
    }
}
