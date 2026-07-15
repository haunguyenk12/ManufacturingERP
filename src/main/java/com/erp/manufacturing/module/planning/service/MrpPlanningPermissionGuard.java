package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.planning.repository.MrpRunRepository;
import com.erp.manufacturing.module.planning.repository.PlanningDemandRepository;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("mrpPlanningPermissionGuard")
@RequiredArgsConstructor
public class MrpPlanningPermissionGuard {

    private final PermissionGuard permissionGuard;
    private final PlanningDemandRepository planningDemandRepository;
    private final MrpRunRepository mrpRunRepository;
    private final SupplySuggestionRepository supplySuggestionRepository;

    @Transactional(readOnly = true)
    public boolean hasDemandAccess(Authentication authentication, String permissionCode, UUID demandId) {
        if (demandId == null) {
            return false;
        }
        return planningDemandRepository.findWithDetailsByPlanningDemandId(demandId)
                .map(demand -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "PLANT", demand.getPlant().getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasRunAccess(Authentication authentication, String permissionCode, UUID runId) {
        if (runId == null) {
            return false;
        }
        return mrpRunRepository.findWithDetailsByMrpRunId(runId)
                .map(run -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "PLANT", run.getPlant().getPlantId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasSuggestionAccess(Authentication authentication, String permissionCode, UUID suggestionId) {
        if (suggestionId == null) {
            return false;
        }
        return supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestionId)
                .map(suggestion -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "PLANT", suggestion.getPlant().getPlantId()))
                .orElse(false);
    }
}
