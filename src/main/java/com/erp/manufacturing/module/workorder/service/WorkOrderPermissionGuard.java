package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("workOrderPermissionGuard")
@RequiredArgsConstructor
public class WorkOrderPermissionGuard {

    private final WorkOrderRepository workOrderRepository;
    private final PermissionGuard permissionGuard;

    @Transactional(readOnly = true)
    public boolean hasWorkOrderAccess(Authentication authentication, String permissionCode, UUID workOrderId) {
        if (workOrderId == null) {
            return false;
        }
        return workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .map(workOrder -> permissionGuard.hasResourceAccess(
                        authentication,
                        permissionCode,
                        "PLANT",
                        workOrder.getPlant().getPlantId()))
                .orElse(false);
    }
}
