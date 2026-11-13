package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.StockTransferLineRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.*;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Warehouse-to-warehouse transfer inside one plant (G4-2, bất biến B150–B152).
 * <p>
 * Every check runs before the first write (C9), and the whole document — balances, both movements of
 * every line, the transfer and its lines — commits in one transaction: one bad line means nothing is
 * moved. Only free stock moves: reserved quantity, quality-held quantity, lots that are not
 * {@code AVAILABLE} and lots past their expiry stay where they are.
 */
@Service
@RequiredArgsConstructor
public class StockTransferService {

    static final String REFERENCE_TYPE = "STOCK_TRANSFER";

    private final StockTransferRepository transferRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemRepository itemRepository;
    private final InventoryLotRepository lotRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final IdempotencySupport idempotency;
    private final TraceIdProvider traceIdProvider;
    private final PlantContextResolver plantContextResolver;
    private final UserLookupService userLookupService;
    private final InventoryMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MOVE', 'WAREHOUSE', #request.fromWarehouseId())"
            + " and @permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MOVE', 'WAREHOUSE', #request.toWarehouseId())")
    @Auditable(action = AuditAction.INVENTORY_TRANSFERRED, entityType = "StockTransfer",
               entityIdExpression = "transferId.toString()", plantId = "#result?.plantId()",
               warehouseId = "#result?.fromWarehouseId()")
    public StockTransferResponse create(StockTransferRequest request, String idempotencyKey, String plantHeader) {
        String key = idempotency.normalizeKey(idempotencyKey);
        Optional<StockTransfer> existing = transferRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            idempotency.ensureSamePayload(existing.get().getPayloadHash(), request);
            return toDetailResponse(existing.get().getTransferId());
        }

        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("toWarehouseId", "must be different from fromWarehouseId"));
        }
        Warehouse from = findActiveWarehouse(request.fromWarehouseId());
        Warehouse to = findActiveWarehouse(request.toWarehouseId());
        Plant plant = from.getPlant();
        if (!plant.getPlantId().equals(to.getPlant().getPlantId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Both warehouses of a transfer must belong to the same plant");
        }
        if (!plant.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive plant cannot transfer stock: " + plant.getPlantId());
        }
        plantContextResolver.ensureMatches(plantHeader, plant.getPlantId());
        ensureNoDuplicatePositions(request.lines());

        List<ResolvedLine> lines = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            lines.add(resolveLine(i, request.lines().get(i), plant));
        }

        Map<PositionKey, StockBalance> sources = lockSources(from, lines);
        ensureEnoughFreeStock(lines, sources);

        // Postgres stores microseconds and rounds; the Windows clock has 100 ns resolution. Without this
        // the create response and every later read disagree on postedAt (same defect as G3, CLAUDE.md §0.51 #3).
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String traceId = traceIdProvider.currentTraceId();
        StockTransfer transfer = transferRepository.save(StockTransfer.builder()
                .plant(plant)
                .fromWarehouse(from)
                .toWarehouse(to)
                .reason(request.reason().trim())
                .postedAt(now)
                .idempotencyKey(key)
                .payloadHash(idempotency.payloadHash(request))
                .traceId(traceId)
                .build());
        String referenceId = transfer.getTransferId().toString();

        for (ResolvedLine line : lines) {
            StockBalance source = sources.get(line.position());
            source.decrease(line.quantity());
            StockBalance destination = findOrCreateBalance(line.item(), to, line.lot());
            destination.increase(line.quantity());
            balanceRepository.save(source);
            balanceRepository.save(destination);

            String childKey = idempotency.childKey(key, line.lineNo());
            StockMovement out = movementRepository.save(movement(
                    line, from, MovementType.TRANSFER_OUT, MovementDirection.OUT, request.reason(),
                    referenceId, childKey, traceId, now));
            StockMovement in = movementRepository.save(movement(
                    line, to, MovementType.TRANSFER_IN, MovementDirection.IN, request.reason(),
                    referenceId, childKey, traceId, now));
            transfer.addLine(StockTransferLine.builder()
                    .lineNo(line.lineNo())
                    .item(line.item())
                    .lot(line.lot())
                    .quantity(line.quantity())
                    .outMovement(out)
                    .inMovement(in)
                    .build());
        }
        StockTransfer saved = transferRepository.saveAndFlush(transfer);
        return mapper.toTransferResponse(saved, username(saved.getCreatedBy()), true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("#warehouseId != null"
            + " ? @permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId)"
            + " : @permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'PLANT', #plantId)")
    public PageResult<StockTransferResponse> list(UUID plantId, UUID warehouseId, String search,
                                                  String plantHeader, Pageable pageable) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        if (warehouseId != null) {
            Warehouse warehouse = organizationLookupService.findWarehouse(warehouseId)
                    .orElseThrow(() -> ExceptionFactory.notFound(
                            ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId));
            if (!warehouse.getPlant().getPlantId().equals(plantId)) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                        "Warehouse does not belong to plant: " + plantId);
            }
        }
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Page<StockTransfer> page = transferRepository.search(plantId, warehouseId, normalizedSearch, pageable);
        Map<UUID, String> usernames = userLookupService.findUsernames(
                page.getContent().stream().map(StockTransfer::getCreatedBy).toList());
        return PageResult.from(page.map(transfer -> mapper.toTransferResponse(
                transfer, transfer.getCreatedBy() == null ? null : usernames.get(transfer.getCreatedBy()), false)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@inventoryPermissionGuard.hasTransferAccess(authentication, 'PERM_INVENTORY_READ', #transferId)")
    public StockTransferResponse get(UUID transferId) {
        return toDetailResponse(transferId);
    }

    private StockTransferResponse toDetailResponse(UUID transferId) {
        StockTransfer transfer = transferRepository.findDetailByTransferId(transferId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Stock transfer", transferId));
        return mapper.toTransferResponse(transfer, username(transfer.getCreatedBy()), true);
    }

    /** D8: one line per item+lot. Rejecting duplicates removes "each line fits, the sum does not". */
    private void ensureNoDuplicatePositions(List<StockTransferLineRequest> requested) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<PositionKey, Integer> firstIndex = new HashMap<>();
        for (int i = 0; i < requested.size(); i++) {
            StockTransferLineRequest line = requested.get(i);
            Integer previous = firstIndex.putIfAbsent(new PositionKey(line.itemId(), line.lotId()), i);
            if (previous != null) {
                errors.put("lines[" + i + "]", "duplicates the item and lot of lines[" + previous + "]");
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
    }

    private ResolvedLine resolveLine(int index, StockTransferLineRequest request, Plant plant) {
        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Item", request.itemId()));
        if (!item.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive item cannot be transferred: lines[" + index + "]");
        }
        if (!item.getCompany().getCompanyId().equals(plant.getCompany().getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Item and warehouses must belong to the same company: lines[" + index + "]");
        }
        if (item.isSerialTracked()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Serial-tracked items cannot be transferred: lines[" + index + "]");
        }
        InventoryLot lot = null;
        if (item.isLotTracked()) {
            if (request.lotId() == null) {
                throw ExceptionFactory.withErrors(ValidationErrorCode.LOT_REQUIRED,
                        Map.of("lines[" + index + "].lotId", "is required for a lot-tracked item"));
            }
            lot = lotRepository.findById(request.lotId())
                    .orElseThrow(() -> ExceptionFactory.notFound(
                            ValidationErrorCode.RESOURCE_NOT_FOUND, "Inventory lot", request.lotId()));
            if (!lot.getItem().getItemId().equals(item.getItemId())) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                        "Lot does not belong to item: lines[" + index + "]");
            }
            if (!lot.canIssue()) {
                throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE,
                        "Lot cannot be transferred in status " + lot.getStatus() + ": lines[" + index + "]");
            }
            if (lot.getExpiresAt() != null && !lot.getExpiresAt().isAfter(Instant.now())) {
                throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE,
                        "Expired lot cannot be transferred: lines[" + index + "]");
            }
        } else if (request.lotId() != null) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Lot can only be provided for lot-tracked items: lines[" + index + "]");
        }
        return new ResolvedLine(index, index + 1, item, lot, request.quantity());
    }

    /**
     * B151: one {@code SELECT … FOR UPDATE} over every source position, ordered by {@code balanceId},
     * so overlapping transfers queue instead of reading the same free quantity — and cannot deadlock.
     */
    private Map<PositionKey, StockBalance> lockSources(Warehouse from, List<ResolvedLine> lines) {
        Set<UUID> itemIds = new LinkedHashSet<>();
        lines.forEach(line -> itemIds.add(line.item().getItemId()));
        Map<PositionKey, StockBalance> byPosition = new HashMap<>();
        for (StockBalance balance : balanceRepository.lockPositionsOfItems(from.getWarehouseId(), itemIds)) {
            UUID lotId = balance.getLot() == null ? null : balance.getLot().getLotId();
            byPosition.put(new PositionKey(balance.getItem().getItemId(), lotId), balance);
        }
        return byPosition;
    }

    private void ensureEnoughFreeStock(List<ResolvedLine> lines, Map<PositionKey, StockBalance> sources) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ResolvedLine line : lines) {
            StockBalance source = sources.get(line.position());
            if (source == null || source.availableQuantity().compareTo(line.quantity()) < 0) {
                errors.put("lines[" + line.index() + "].quantity",
                        "Requested quantity exceeds free available stock");
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.INSUFFICIENT_STOCK, errors);
        }
    }

    private StockBalance findOrCreateBalance(Item item, Warehouse warehouse, InventoryLot lot) {
        Optional<StockBalance> existing = lot == null
                ? balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(
                        item.getItemId(), warehouse.getWarehouseId())
                : balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                        item.getItemId(), warehouse.getWarehouseId(), lot.getLotId());
        return existing.orElseGet(() -> StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .quantity(BigDecimal.ZERO)
                .build());
    }

    private StockMovement movement(ResolvedLine line, Warehouse warehouse, MovementType type,
                                   MovementDirection direction, String reason, String referenceId,
                                   String childKey, String traceId, Instant now) {
        return StockMovement.builder()
                .traceId(traceId)
                .item(line.item())
                .warehouse(warehouse)
                .lot(line.lot())
                .movementType(type)
                .direction(direction)
                .quantity(line.quantity())
                .reason(reason.trim())
                .referenceType(REFERENCE_TYPE)
                .referenceId(referenceId)
                .idempotencyKey(childKey)
                .createdAt(now)
                .build();
    }

    private Warehouse findActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = organizationLookupService.findWarehouse(warehouseId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId));
        if (!warehouse.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive warehouse cannot transfer stock: " + warehouseId);
        }
        return warehouse;
    }

    private String username(UUID userId) {
        return userId == null ? null : userLookupService.findUsernames(List.of(userId)).get(userId);
    }

    private record PositionKey(UUID itemId, UUID lotId) {}

    private record ResolvedLine(int index, int lineNo, Item item, InventoryLot lot, BigDecimal quantity) {
        PositionKey position() {
            return new PositionKey(item.getItemId(), lot == null ? null : lot.getLotId());
        }
    }
}
