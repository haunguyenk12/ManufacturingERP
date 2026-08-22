package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.MovementType;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.dto.InventoryLotDetailResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryLotResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryLotStatusChangeRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.workorder.service.query.LotQcOriginLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryLotService {

    private static final Set<LotStatus> ALLOWED_TARGET_STATUSES =
            Set.of(LotStatus.AVAILABLE, LotStatus.HOLD, LotStatus.REJECTED);

    private final InventoryLotRepository lotRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final InventoryMovementService movementService;
    private final LotQcOriginLookupService lotQcOriginLookupService;
    private final InventoryMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId)")
    public PageResult<InventoryLotResponse> list(UUID warehouseId, UUID itemId, LotStatus status,
                                                 String search, Instant expiryFrom, Instant expiryTo,
                                                 Pageable pageable) {
        Page<StockBalance> page = balanceRepository.searchLots(
                warehouseId, itemId, status, search, expiryFrom, expiryTo, pageable);
        return PageResult.from(page.map(balance -> mapper.toLotResponse(balance, findOriginMovement(balance.getLot()))));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("#warehouseId != null ? "
            + "@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'WAREHOUSE', #warehouseId) : "
            + "@inventoryPermissionGuard.hasLotCompanyAccess(authentication, 'PERM_INVENTORY_READ', #lotId)")
    public InventoryLotDetailResponse get(UUID lotId, UUID warehouseId) {
        InventoryLot lot = findLot(lotId);
        if (warehouseId == null) {
            List<StockBalance> balances = balanceRepository.findByLotLotId(lotId);
            return mapper.toLotDetailResponse(lot, balances, findOriginMovement(lot));
        }

        StockBalance balance = balanceRepository
                .findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                        lot.getItem().getItemId(), warehouseId, lotId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Stock balance for lot in warehouse", lotId));
        return mapper.toLotDetailResponse(
                lot,
                List.of(balance),
                findOriginMovement(lot, warehouseId));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MOVE', 'WAREHOUSE', #request.warehouseId())")
    @Auditable(action = AuditAction.INVENTORY_LOT_STATUS_CHANGED, entityType = "InventoryLot", entityIdExpression = "lotId.toString()")
    public InventoryLotResponse changeStatus(UUID lotId, InventoryLotStatusChangeRequest request,
                                             String idempotencyKey) {
        if (!ALLOWED_TARGET_STATUSES.contains(request.newStatus())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Lot status can only be changed to AVAILABLE, HOLD, or REJECTED through this endpoint");
        }

        InventoryLot lot = findLot(lotId);
        if (lot.getStatus() == LotStatus.HOLD && request.newStatus() != LotStatus.HOLD
                && lotQcOriginLookupService.requiresQcDispositionBeforeRelease(lotId)) {
            throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE,
                    "Lot is on hold from a production receipt and must be released via QC disposition");
        }

        StockBalance balance = balanceRepository
                .findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                        lot.getItem().getItemId(), request.warehouseId(), lotId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Stock balance for lot", lotId));

        movementService.changeLotStatus(new LotStatusChangeCommand(
                lot.getItem().getItemId(),
                request.warehouseId(),
                lotId,
                request.newStatus(),
                balance.getQuantity(),
                request.reason(),
                request.referenceType(),
                request.referenceId()), idempotencyKey);

        // Same persistence context as changeLotStatus (REQUIRED propagation) — `lot` is the identical
        // managed instance it mutated via setStatus(), so it already reflects the new status here.
        return mapper.toLotResponse(balance, findOriginMovement(lot));
    }

    private InventoryLot findLot(UUID lotId) {
        return lotRepository.findById(lotId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Lot", lotId));
    }

    private StockMovement findOriginMovement(InventoryLot lot) {
        return movementRepository
                .findFirstByLotLotIdAndMovementTypeOrderByCreatedAtAsc(lot.getLotId(), MovementType.RECEIVE)
                .orElse(null);
    }

    private StockMovement findOriginMovement(InventoryLot lot, UUID warehouseId) {
        return movementRepository
                .findFirstByWarehouseWarehouseIdAndLotLotIdAndMovementTypeOrderByCreatedAtAsc(
                        warehouseId, lot.getLotId(), MovementType.RECEIVE)
                .orElse(null);
    }
}
