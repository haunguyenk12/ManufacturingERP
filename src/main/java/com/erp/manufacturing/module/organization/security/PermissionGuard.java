package com.erp.manufacturing.module.organization.security;

import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.UserRoleAssignmentRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component("permissionGuard")
@RequiredArgsConstructor
public class PermissionGuard {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final UserRoleAssignmentRepository assignmentRepository;
    private final PlantRepository plantRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public boolean hasPermission(Authentication authentication, String permissionCode) {
        String normalizedPermission = normalizePermissionCode(permissionCode);
        if (normalizedPermission == null) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        return principalUserId(authentication)
                .map(userId -> assignmentRepository.existsActivePermissionInScopeType(
                        userId,
                        normalizedPermission,
                        ScopeType.GLOBAL,
                        Instant.now(),
                        AssignmentStatus.ACTIVE,
                        RoleStatus.ACTIVE,
                        OrganizationStatus.ACTIVE,
                        OrganizationStatus.ACTIVE))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasResourceAccess(Authentication authentication,
                                     String permissionCode,
                                     String resourceType,
                                     UUID resourceId) {
        if (resourceId == null) {
            return false;
        }

        String normalizedPermission = normalizePermissionCode(permissionCode);
        ScopeResourceType type = normalizeResourceType(resourceType);
        if (normalizedPermission == null || type == null) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }

        Optional<UUID> userId = principalUserId(authentication);
        if (userId.isEmpty()) {
            return false;
        }

        if (hasPermission(authentication, normalizedPermission)) {
            return true;
        }

        return switch (type) {
            case COMPANY -> hasDirectResourcePermission(userId.get(), normalizedPermission, ScopeResourceType.COMPANY, resourceId);
            case PLANT -> hasPlantAccess(userId.get(), normalizedPermission, resourceId);
            case WAREHOUSE -> hasWarehouseAccess(userId.get(), normalizedPermission, resourceId);
        };
    }

    /**
     * Authorizes company-owned master data for a user assigned either to the company itself or to
     * one of its plants. This deliberately does not change {@link #hasResourceAccess}: most
     * company resources must keep the normal top-down scope rule, while shared item master data is
     * consumed by every plant in the owning company.
     */
    @Transactional(readOnly = true)
    public boolean hasCompanyOrPlantAccess(Authentication authentication,
                                           String permissionCode,
                                           UUID companyId) {
        if (companyId == null) {
            return false;
        }

        String normalizedPermission = normalizePermissionCode(permissionCode);
        if (normalizedPermission == null) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }

        Optional<UUID> userId = principalUserId(authentication);
        if (userId.isEmpty()) {
            return false;
        }

        if (hasPermission(authentication, normalizedPermission)
                || hasDirectResourcePermission(
                        userId.get(), normalizedPermission, ScopeResourceType.COMPANY, companyId)) {
            return true;
        }

        return plantRepository.findByCompanyCompanyId(companyId, Pageable.unpaged()).stream()
                .anyMatch(plant -> hasDirectResourcePermission(
                        userId.get(), normalizedPermission, ScopeResourceType.PLANT, plant.getPlantId()));
    }

    private boolean hasPlantAccess(UUID userId, String permissionCode, UUID plantId) {
        if (hasDirectResourcePermission(userId, permissionCode, ScopeResourceType.PLANT, plantId)) {
            return true;
        }
        return plantRepository.findById(plantId)
                .map(plant -> hasDirectResourcePermission(
                        userId,
                        permissionCode,
                        ScopeResourceType.COMPANY,
                        plant.getCompany().getCompanyId()))
                .orElse(false);
    }

    private boolean hasWarehouseAccess(UUID userId, String permissionCode, UUID warehouseId) {
        if (hasDirectResourcePermission(userId, permissionCode, ScopeResourceType.WAREHOUSE, warehouseId)) {
            return true;
        }
        return warehouseRepository.findById(warehouseId)
                .map(warehouse -> {
                    UUID plantId = warehouse.getPlant().getPlantId();
                    UUID companyId = warehouse.getPlant().getCompany().getCompanyId();
                    return hasDirectResourcePermission(userId, permissionCode, ScopeResourceType.PLANT, plantId)
                            || hasDirectResourcePermission(userId, permissionCode, ScopeResourceType.COMPANY, companyId);
                })
                .orElse(false);
    }

    private boolean hasDirectResourcePermission(UUID userId,
                                                String permissionCode,
                                                ScopeResourceType resourceType,
                                                UUID resourceId) {
        return assignmentRepository.existsActiveResourcePermission(
                userId,
                permissionCode,
                resourceType,
                resourceId,
                Instant.now(),
                AssignmentStatus.ACTIVE,
                RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE,
                OrganizationStatus.ACTIVE);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.isGlobalAdmin()
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }

    private Optional<UUID> principalUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.ofNullable(principal.getUserId());
        }
        return Optional.empty();
    }

    private String normalizePermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return null;
        }
        String normalized = permissionCode.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("PERM_") ? normalized : "PERM_" + normalized;
    }

    private ScopeResourceType normalizeResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return null;
        }
        try {
            return ScopeResourceType.valueOf(resourceType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
