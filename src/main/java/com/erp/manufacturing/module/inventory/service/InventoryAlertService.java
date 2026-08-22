package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.DashboardRecentMovementResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryAlertLineResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardShortageSummaryResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAlertService {

    static final int DEFAULT_DASHBOARD_LIMIT = 10;
    static final int MAX_DASHBOARD_LIMIT = 20;

    private final ItemWarehouseSettingRepository settingRepository;
    private final InventoryAvailabilityService availabilityService;
    private final OrganizationLookupService organizationLookupService;
    private final StockMovementRepository stockMovementRepository;
    private final UserLookupService userLookupService;
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

    /**
     * @param lowStockLimit how many alert lines to return, clamped to {@code [1, 20]};
     *        {@code null} means {@value #DEFAULT_DASHBOARD_LIMIT}
     * @param movementLimit how many ledger rows to return, same clamping
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', #scopeType.name(), #scopeId)")
    public InventoryDashboardResponse getDashboard(ScopeResourceType scopeType,
                                                   UUID scopeId,
                                                   Integer lowStockLimit,
                                                   Integer movementLimit) {
        OrganizationScopeResolution scope = organizationLookupService.resolveScope(scopeType, scopeId);
        List<InventoryAlertLineResponse> lines = buildAlertLines(scope);
        List<InventoryAlertLineResponse> actionableLines = lines.stream()
                .filter(line -> !InventoryAlertStatus.OK.name().equals(line.status()))
                .limit(clampLimit(lowStockLimit))
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
                loadRecentMovements(scope, clampLimit(movementLimit)),
                Instant.now());
    }

    private int clampLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_DASHBOARD_LIMIT;
        }
        return Math.max(1, Math.min(requested, MAX_DASHBOARD_LIMIT));
    }

    /**
     * Two queries for the whole block regardless of page size (rule C14): the ledger page itself, then
     * one batch lookup that turns {@code created_by} into a username.
     */
    private List<DashboardRecentMovementResponse> loadRecentMovements(OrganizationScopeResolution scope, int limit) {
        if (scope.warehouseIds().isEmpty()) {
            return List.of();
        }

        List<StockMovement> movements = stockMovementRepository.findRecentByWarehouseIds(
                scope.warehouseIds(), PageRequest.of(0, limit));
        if (movements.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> usernames = userLookupService.findUsernames(movements.stream()
                .map(StockMovement::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        return movements.stream()
                .map(movement -> mapper.toDashboardMovementResponse(
                        movement,
                        movement.getCreatedBy() == null ? null : usernames.get(movement.getCreatedBy())))
                .toList();
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
        Map<InventoryAvailabilityService.ItemWarehouseAvailabilityKey,
                InventoryAvailabilityService.WarehouseStockQuantity> quantitiesByItemWarehouse =
                availabilityService.getStockQuantitiesByWarehouse(itemIds, scope.warehouseIds());

        return settings.stream()
                .map(setting -> toAlertLine(setting, quantitiesByItemWarehouse.getOrDefault(
                        new InventoryAvailabilityService.ItemWarehouseAvailabilityKey(
                                setting.getItem().getItemId(),
                                setting.getWarehouse().getWarehouseId()),
                        InventoryAvailabilityService.WarehouseStockQuantity.zero())))
                .sorted(alertComparator())
                .toList();
    }

    private InventoryAlertLineResponse toAlertLine(ItemWarehouseSetting setting,
                                                   InventoryAvailabilityService.WarehouseStockQuantity quantity) {
        InventoryAlertStatus status = resolveStatus(setting, quantity.availableQuantity());
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
                quantity.availableQuantity(),
                status.name(),
                setting.getItem().getUnit(),
                quantity.onHandQuantity(),
                quantity.reservedQuantity(),
                quantity.qualityHoldQuantity(),
                shortageQuantity(setting, quantity.availableQuantity()));
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

    /**
     * How much has to be replenished for the line to reach {@code OK}, which means clearing
     * <b>both</b> thresholds — not only the reorder point. Using the reorder point alone would report
     * {@code 0} for every {@code LOW_STOCK} line, and {@code LOW_STOCK} is by definition the band
     * between the two thresholds.
     */
    private BigDecimal shortageQuantity(ItemWarehouseSetting setting, BigDecimal availableQuantity) {
        return positiveDifference(setting.getSafetyStock().max(setting.getReorderPoint()), availableQuantity);
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

    /**
     * Worst first: {@code REORDER_NEEDED} before {@code LOW_STOCK} before {@code OK}, then the largest
     * shortage, then a stable alphabetical fallback so two equally urgent lines never swap places
     * between calls.
     */
    private Comparator<InventoryAlertLineResponse> alertComparator() {
        return Comparator
                .comparingInt((InventoryAlertLineResponse line) -> statusRank(line.status()))
                .thenComparing(InventoryAlertLineResponse::shortageQuantity, Comparator.reverseOrder())
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
