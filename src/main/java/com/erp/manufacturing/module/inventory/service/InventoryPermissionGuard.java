package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.inventory.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("inventoryPermissionGuard")
@RequiredArgsConstructor
public class InventoryPermissionGuard {

    private final ItemRepository itemRepository;
    private final InventoryLotRepository lotRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final StockTransferRepository transferRepository;
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasItemAccess(Authentication authentication, String permissionCode, UUID itemId) {
        if (itemId == null) {
            return false;
        }
        return itemRepository.findById(itemId)
                .map(item -> permissionGuard.hasCompanyOrPlantAccess(
                        authentication,
                        permissionCode,
                        item.getCompany().getCompanyId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasItemCompanyAccess(Authentication authentication,
                                        String permissionCode,
                                        UUID companyId) {
        return permissionGuard.hasCompanyOrPlantAccess(authentication, permissionCode, companyId);
    }

    /**
     * Resolves {@code lot → item → company} for the company-wide detail mode (C2-2). The normal FE
     * flow supplies {@code warehouseId} and is authorized directly by {@code PermissionGuard}; this
     * stricter fallback is only for callers requesting balances across every warehouse.
     */
    @Transactional(readOnly = true)
    public boolean hasLotCompanyAccess(Authentication authentication, String permissionCode, UUID lotId) {
        if (lotId == null) {
            return false;
        }
        return lotRepository.findById(lotId)
                .map(lot -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "COMPANY",
                        lot.getItem().getCompany().getCompanyId()))
                .orElse(false);
    }

    /**
     * Lot status change (G4-1, bất biến B149). Lot status lives on {@code inventory_lots}, so changing
     * it acts on <b>every</b> warehouse holding the lot — not only on {@code warehouseId} from the
     * request. Before G4 only that one warehouse was checked, so a user scoped to warehouse A could
     * change the quality status of stock sitting in warehouse B outside their scope. Now the caller
     * needs {@code permissionCode} in the requested warehouse <em>and</em> in each warehouse that
     * still holds stock of the lot. A denial does not say which warehouse failed.
     */
    @Transactional(readOnly = true)
    public boolean hasLotPositionsAccess(Authentication authentication, String permissionCode,
                                         UUID lotId, UUID warehouseId) {
        if (lotId == null || warehouseId == null
                || !permissionGuard.hasResourceAccess(authentication, permissionCode, "WAREHOUSE", warehouseId)) {
            return false;
        }
        return balanceRepository.findWarehouseIdsHoldingLot(lotId).stream()
                .filter(id -> !id.equals(warehouseId))
                .allMatch(id -> permissionGuard.hasResourceAccess(authentication, permissionCode, "WAREHOUSE", id));
    }

    /** Movement detail (G4-4): readable by whoever may read the warehouse the movement was posted in. */
    @Transactional(readOnly = true)
    public boolean hasMovementAccess(Authentication authentication, String permissionCode, UUID movementId) {
        if (movementId == null) {
            return false;
        }
        return movementRepository.findWarehouseIdByMovementId(movementId)
                .map(warehouseId -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "WAREHOUSE", warehouseId))
                .orElse(false);
    }

    /**
     * Transfer detail (G4-2): readable by whoever may read either side of it. An unknown id is a
     * denial (403), the same shape as every other aggregate-identified guard in this repo.
     */
    @Transactional(readOnly = true)
    public boolean hasTransferAccess(Authentication authentication, String permissionCode, UUID transferId) {
        if (transferId == null) {
            return false;
        }
        return transferRepository.findById(transferId)
                .map(transfer -> permissionGuard.hasResourceAccess(authentication, permissionCode, "WAREHOUSE",
                                transfer.getFromWarehouse().getWarehouseId())
                        || permissionGuard.hasResourceAccess(authentication, permissionCode, "WAREHOUSE",
                                transfer.getToWarehouse().getWarehouseId()))
                .orElse(false);
    }
}
