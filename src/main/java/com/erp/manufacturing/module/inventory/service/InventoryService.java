package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.dto.*;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryMovementService movementService;
    private final WarehouseRepository warehouseRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final InventoryMapper mapper;
    private final UserLookupService userLookupService;
    private final LotQcOwnershipGuard lotQcOwnershipGuard;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MOVE', 'WAREHOUSE', #request.warehouseId())")
    @Auditable(action = AuditAction.INVENTORY_RECEIVED, entityType = "StockMovement", entityIdExpression = "movementId.toString()",
               warehouseId = "#result?.warehouseId()")
    public StockMovementResponse receive(StockReceiveRequest request, String idempotencyKey) {
        StockMovement movement = movementService.receive(new InventoryReceiveCommand(
                request.itemId(),
                request.warehouseId(),
                request.lotId(),
                request.lotCode(),
                request.quantity(),
                request.reason(),
                request.referenceType(),
                request.referenceId(), null, null, request.expiresAt()), idempotencyKey).movement();
        return toResponse(movement);
    }

    /** Manual issue: free stock only, and — unlike work-order issue — never from a lot past its expiry (G4). */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MOVE', 'WAREHOUSE', #request.warehouseId())")
    @Auditable(action = AuditAction.INVENTORY_ISSUED, entityType = "StockMovement", entityIdExpression = "movementId.toString()",
               warehouseId = "#result?.warehouseId()")
    public StockMovementResponse issue(StockIssueRequest request, String idempotencyKey) {
        StockMovement movement = movementService.issueManual(new InventoryIssueCommand(
                request.itemId(),
                request.warehouseId(),
                request.lotId(),
                request.lotCode(),
                request.quantity(),
                request.reason(),
                request.referenceType(),
                request.referenceId(), null, null), idempotencyKey).movement();
        return toResponse(movement);
    }

    /** CF-1 (B263): a lot QC still owns (pending purchase/production QC, or a QC-rejected child) is not adjustable. */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MOVE', 'WAREHOUSE', #request.warehouseId())")
    @Auditable(action = AuditAction.INVENTORY_ADJUSTED, entityType = "StockMovement", entityIdExpression = "movementId.toString()",
               warehouseId = "#result?.warehouseId()")
    public StockMovementResponse adjust(StockAdjustRequest request, String idempotencyKey) {
        StockMovement movement = movementService.adjust(new InventoryAdjustCommand(
                request.itemId(),
                request.warehouseId(),
                request.lotId(),
                request.lotCode(),
                request.quantityDelta(),
                request.reason(),
                request.referenceType(),
                request.referenceId(), null, null), idempotencyKey,
                lotQcOwnershipGuard::ensureAdjustable).movement();
        return toResponse(movement);
    }

    /** Absolute stock count (G4-3). {@code POST /adjust} keeps its signed-delta meaning unchanged. */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MOVE', 'WAREHOUSE', #request.warehouseId())")
    @Auditable(action = AuditAction.INVENTORY_COUNTED, entityType = "StockMovement",
               entityIdExpression = "movement.movementId.toString()", warehouseId = "#result?.balance()?.warehouseId()")
    public StockCountResponse count(StockCountRequest request, String idempotencyKey) {
        InventoryCountResult result = movementService.count(new InventoryCountCommand(
                request.itemId(),
                request.warehouseId(),
                request.lotId(),
                request.countedQuantity(),
                request.expectedVersion(),
                request.reason()), idempotencyKey);
        return new StockCountResponse(
                result.previousQuantity(),
                request.countedQuantity(),
                request.countedQuantity().subtract(result.previousQuantity()),
                toResponse(result.movement()),
                mapper.toResponse(result.balance()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId)")
    public PageResult<StockBalanceResponse> listBalances(UUID warehouseId, UUID itemId, Pageable pageable) {
        ensureWarehouseExists(warehouseId);
        Page<com.erp.manufacturing.module.inventory.domain.StockBalance> page = itemId == null
                ? balanceRepository.findByWarehouseWarehouseId(warehouseId, pageable)
                : balanceRepository.findByWarehouseWarehouseIdAndItemItemId(warehouseId, itemId, pageable);
        return PageResult.from(page.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId)")
    public PageResult<StockBalanceAggregateResponse> listAggregateBalances(
            UUID warehouseId, UUID itemId, String search, Pageable pageable) {
        ensureWarehouseExists(warehouseId);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return PageResult.from(balanceRepository.aggregateBalances(
                        warehouseId, itemId, normalizedSearch, Instant.now(), pageable)
                .map(row -> new StockBalanceAggregateResponse(
                        row.getItemId(), row.getItemCode(), row.getItemName(), row.getUom(),
                        row.getWarehouseId(), row.getOnHandQuantity(), row.getReservedQuantity(),
                        row.getAvailableQuantity(), row.getQualityHoldQuantity(), row.getRejectedQuantity(),
                        row.getExpiredQuantity(), row.getHeldLotQuantity(), row.getLotCount(),
                        row.getUpdatedAt())));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId)")
    public PageResult<StockMovementResponse> listMovements(UUID warehouseId, UUID itemId, UUID lotId, Pageable pageable) {
        ensureWarehouseExists(warehouseId);
        Page<StockMovement> page;
        if (itemId != null && lotId != null) {
            page = movementRepository.findByWarehouseWarehouseIdAndItemItemIdAndLotLotId(
                    warehouseId, itemId, lotId, pageable);
        } else if (itemId != null) {
            page = movementRepository.findByWarehouseWarehouseIdAndItemItemId(warehouseId, itemId, pageable);
        } else if (lotId != null) {
            page = movementRepository.findByWarehouseWarehouseIdAndLotLotId(warehouseId, lotId, pageable);
        } else {
            page = movementRepository.findByWarehouseWarehouseId(warehouseId, pageable);
        }
        // One batch for the whole page (C14), never one lookup per row.
        Map<UUID, String> usernames = userLookupService.findUsernames(
                page.getContent().stream().map(StockMovement::getCreatedBy).toList());
        return PageResult.from(page.map(movement -> mapper.toResponse(movement,
                movement.getCreatedBy() == null ? null : usernames.get(movement.getCreatedBy()))));
    }

    /** Movement detail (G4-4); scope is the warehouse the movement was posted in. */
    @Transactional(readOnly = true)
    @PreAuthorize("@inventoryPermissionGuard.hasMovementAccess(authentication, 'PERM_INVENTORY_READ', #movementId)")
    public StockMovementResponse getMovement(UUID movementId) {
        StockMovement movement = movementRepository.findDetailByMovementId(movementId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Stock movement", movementId));
        return toResponse(movement);
    }

    private StockMovementResponse toResponse(StockMovement movement) {
        UUID actor = movement.getCreatedBy();
        return mapper.toResponse(movement,
                actor == null ? null : userLookupService.findUsernames(List.of(actor)).get(actor));
    }

    private void ensureWarehouseExists(UUID warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId);
        }
    }
}
