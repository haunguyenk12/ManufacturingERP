package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.purchasing.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("purchasingPermissionGuard")
@RequiredArgsConstructor
public class PurchasingPermissionGuard {

    private final PermissionGuard permissionGuard;
    private final ItemLookupService itemLookupService;
    private final SupplierRepository supplierRepository;
    private final ItemSupplierRepository itemSupplierRepository;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;

    @Transactional(readOnly = true)
    public boolean hasItemAccess(Authentication authentication, String permissionCode, UUID itemId) {
        if (itemId == null) {
            return false;
        }
        return permissionGuard.hasResourceAccess(
                authentication,
                permissionCode,
                "COMPANY",
                itemLookupService.getItem(itemId).getCompany().getCompanyId());
    }

    @Transactional(readOnly = true)
    public boolean hasSupplierAccess(Authentication authentication, String permissionCode, UUID supplierId) {
        if (supplierId == null) {
            return false;
        }
        return supplierRepository.findWithCompanyBySupplierId(supplierId)
                .map(supplier -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "COMPANY",
                        supplier.getCompany().getCompanyId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasItemSupplierAccess(Authentication authentication, String permissionCode, UUID itemSupplierId) {
        if (itemSupplierId == null) {
            return false;
        }
        return itemSupplierRepository.findWithDetailsByItemSupplierId(itemSupplierId)
                .map(itemSupplier -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "COMPANY",
                        itemSupplier.getItem().getCompany().getCompanyId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasRequisitionAccess(Authentication authentication, String permissionCode, UUID requisitionId) {
        if (requisitionId == null) {
            return false;
        }
        return purchaseRequisitionRepository.findWithDetailsByPurchaseRequisitionId(requisitionId)
                .map(requisition -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "PLANT",
                        requisition.getPlant().getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasOrderAccess(Authentication authentication, String permissionCode, UUID orderId) {
        if (orderId == null) {
            return false;
        }
        return purchaseOrderRepository.findWithDetailsByPurchaseOrderId(orderId)
                .map(order -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "PLANT",
                        order.getPlant().getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasReceiptAccess(Authentication authentication, String permissionCode, UUID receiptId) {
        if (receiptId == null) {
            return false;
        }
        return goodsReceiptRepository.findWithDetailsByGoodsReceiptId(receiptId)
                .map(receipt -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "WAREHOUSE",
                        receipt.getWarehouse().getWarehouseId()))
                .orElse(false);
    }
}
