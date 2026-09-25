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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryMovementService movementService;
    private final WarehouseRepository warehouseRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final InventoryMapper mapper;

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
                request.referenceId(), null, null), idempotencyKey).movement();
        return mapper.toResponse(movement);
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MOVE', 'WAREHOUSE', #request.warehouseId())")
    @Auditable(action = AuditAction.INVENTORY_ISSUED, entityType = "StockMovement", entityIdExpression = "movementId.toString()",
               warehouseId = "#result?.warehouseId()")
    public StockMovementResponse issue(StockIssueRequest request, String idempotencyKey) {
        StockMovement movement = movementService.issue(new InventoryIssueCommand(
                request.itemId(),
                request.warehouseId(),
                request.lotId(),
                request.lotCode(),
                request.quantity(),
                request.reason(),
                request.referenceType(),
                request.referenceId(), null, null), idempotencyKey).movement();
        return mapper.toResponse(movement);
    }

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
                request.referenceId(), null, null), idempotencyKey).movement();
        return mapper.toResponse(movement);
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
            UUID warehouseId, UUID itemId, Pageable pageable) {
        ensureWarehouseExists(warehouseId);
        return PageResult.from(balanceRepository.aggregateBalances(warehouseId, itemId, pageable)
                .map(row -> new StockBalanceAggregateResponse(
                        row.getItemId(), row.getItemCode(), row.getItemName(), row.getUom(),
                        row.getWarehouseId(), row.getOnHandQuantity(), row.getReservedQuantity(),
                        row.getAvailableQuantity(), row.getQualityHoldQuantity(), row.getRejectedQuantity(),
                        row.getExpiredQuantity(), row.getLotCount(), row.getUpdatedAt())));
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
        return PageResult.from(page.map(mapper::toResponse));
    }

    private void ensureWarehouseExists(UUID warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId);
        }
    }
}
