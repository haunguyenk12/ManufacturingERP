package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component("workOrderPermissionGuard")
@RequiredArgsConstructor
public class WorkOrderPermissionGuard {

    private final WorkOrderRepository workOrderRepository;
    private final PermissionGuard permissionGuard;
    private final MaterialIssueRepository materialIssueRepository;
    private final MaterialReturnRepository materialReturnRepository;

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

    /**
     * G8b (FE §1.4): reading material issues needs any one of several permissions — the requester's
     * {@code MANAGE}, the manager's {@code APPROVE} or {@code OVERRIDE} — so the approval queue opens for a
     * manager who never posts issues. Each code is checked on the work order's plant, like the single form.
     */
    @Transactional(readOnly = true)
    public boolean hasAnyWorkOrderAccess(Authentication authentication, List<String> permissionCodes, UUID workOrderId) {
        if (workOrderId == null || permissionCodes == null) {
            return false;
        }
        return workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .map(workOrder -> permissionCodes.stream().anyMatch(code -> permissionGuard.hasResourceAccess(
                        authentication, code, "PLANT", workOrder.getPlant().getPlantId())))
                .orElse(false);
    }

    /** G8b: the same check for an endpoint identified by the material issue alone. */
    @Transactional(readOnly = true)
    public boolean hasMaterialIssueAccess(Authentication authentication, List<String> permissionCodes, UUID issueId) {
        if (issueId == null) {
            return false;
        }
        return materialIssueRepository.findWorkOrderIdByIssueId(issueId)
                .map(workOrderId -> hasAnyWorkOrderAccess(authentication, permissionCodes, workOrderId))
                .orElse(false);
    }

    /** G8d: the same check for an endpoint identified by the material return alone. */
    @Transactional(readOnly = true)
    public boolean hasMaterialReturnAccess(Authentication authentication, List<String> permissionCodes, UUID materialReturnId) {
        if (materialReturnId == null) {
            return false;
        }
        return materialReturnRepository.findWorkOrderIdByMaterialReturnId(materialReturnId)
                .map(workOrderId -> hasAnyWorkOrderAccess(authentication, permissionCodes, workOrderId))
                .orElse(false);
    }
}
