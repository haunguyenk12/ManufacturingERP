package com.erp.manufacturing.module.dataimport.security;

import com.erp.manufacturing.module.dataimport.repository.ImportProfileRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("dataImportPermissionGuard")
@RequiredArgsConstructor
public class DataImportPermissionGuard {

    private final PermissionGuard permissionGuard;
    private final ImportRunRepository runRepository;
    private final ImportProfileRepository profileRepository;

    public boolean hasTargetReadAccess(Authentication authentication, UUID companyId) {
        return companyId == null
                ? permissionGuard.hasPermission(authentication, "PERM_DATA_IMPORT_READ")
                : permissionGuard.hasResourceAccess(
                        authentication, "PERM_DATA_IMPORT_READ", "COMPANY", companyId);
    }

    @Transactional(readOnly = true)
    public boolean hasRunAccess(Authentication authentication, String permission, UUID runId) {
        return runId != null && runRepository.findById(runId)
                .map(run -> permissionGuard.hasResourceAccess(authentication, permission, "COMPANY",
                        run.getCompany().getCompanyId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasProfileAccess(Authentication authentication, String permission, UUID profileId) {
        return profileId != null && profileRepository.findById(profileId)
                .map(profile -> profile.getCompany() == null
                        ? permissionGuard.hasPermission(authentication, permission)
                        : permissionGuard.hasResourceAccess(authentication, permission, "COMPANY",
                                profile.getCompany().getCompanyId()))
                .orElse(false);
    }
}
