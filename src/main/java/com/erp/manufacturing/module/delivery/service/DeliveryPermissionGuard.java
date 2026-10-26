package com.erp.manufacturing.module.delivery.service;

import com.erp.manufacturing.module.delivery.repository.DeliveryRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * G10b (FE Q26): a delivery belongs to its plant. Reading needs any one of READ, MANAGE or POST — POST does not imply
 * MANAGE. An id that does not exist is "no access" (403), like every aggregate guard of this codebase (§0.50 #9).
 */
@Component("deliveryPermissionGuard")
@RequiredArgsConstructor
public class DeliveryPermissionGuard {

    static final String READ = "PERM_DELIVERY_READ";
    static final String MANAGE = "PERM_DELIVERY_MANAGE";
    static final String POST = "PERM_DELIVERY_POST";
    static final List<String> ANY = List.of(READ, MANAGE, POST);

    private final PermissionGuard permissionGuard;
    private final DeliveryRepository deliveryRepository;
    private final SalesOrderCommercialLookupService salesOrderLookupService;

    @Transactional(readOnly = true)
    public boolean hasDeliveryAccess(Authentication authentication, String permissionCode, UUID deliveryId) {
        return deliveryId != null && deliveryRepository.findById(deliveryId)
                .map(delivery -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "PLANT", delivery.getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canReadDelivery(Authentication authentication, UUID deliveryId) {
        return deliveryId != null && deliveryRepository.findById(deliveryId)
                .map(delivery -> canReadPlant(authentication, delivery.getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canReadPlant(Authentication authentication, UUID plantId) {
        return plantId != null && ANY.stream()
                .anyMatch(code -> permissionGuard.hasResourceAccess(authentication, code, "PLANT", plantId));
    }

    /** Candidates and sources of a sales order: delivery authority on the order's plant. */
    @Transactional(readOnly = true)
    public boolean canReadOrder(Authentication authentication, UUID salesOrderId) {
        return salesOrderLookupService.findOrder(salesOrderId)
                .map(order -> canReadPlant(authentication, order.getPlant().getPlantId()))
                .orElse(false);
    }

    /** Creating a delivery for a sales order: {@code PERM_DELIVERY_MANAGE} on the order's plant. */
    @Transactional(readOnly = true)
    public boolean canManageOrder(Authentication authentication, UUID salesOrderId) {
        return salesOrderLookupService.findOrder(salesOrderId)
                .map(order -> permissionGuard.hasResourceAccess(authentication, MANAGE, "PLANT",
                        order.getPlant().getPlantId()))
                .orElse(false);
    }
}
