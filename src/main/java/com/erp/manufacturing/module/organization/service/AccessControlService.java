package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.dto.*;
import com.erp.manufacturing.module.organization.mapper.OrganizationMapper;
import com.erp.manufacturing.module.organization.repository.*;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.organization.repository.RoleRepository;
import com.erp.manufacturing.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PlantRepository plantRepository;
    private final WarehouseRepository warehouseRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AccessScopeRepository accessScopeRepository;
    private final AccessScopeResourceRepository accessScopeResourceRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final OrganizationMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    public PageResult<RoleResponse> listRoles(Pageable pageable) {
        return PageResult.from(roleRepository.findAll(pageable).map(mapper::toResponse));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.ROLE_CREATED, entityType = "Role", entityIdExpression = "roleId.toString()",
               companyId = "#result?.companyId()")
    public RoleResponse createRole(RoleCreateRequest request) {
        UUID companyId = request.companyId();
        if (companyId != null) {
            ensureActiveCompany(companyId);
        }

        String code = normalizeCode(request.code());
        if (isReservedAdminCode(code)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Reserved administrator role code cannot be used for a custom role");
        }
        boolean exists = companyId == null
                ? roleRepository.existsByCodeAndCompanyIdIsNull(code)
                : roleRepository.existsByCodeAndCompanyId(code, companyId);
        if (exists) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Role code", code);
        }

        Role role = Role.builder()
                .companyId(companyId)
                .code(code)
                .name(request.name().trim())
                .description(request.description())
                .system(false)
                .status(RoleStatus.ACTIVE)
                .build();
        return mapper.toResponse(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    public RoleResponse getRole(UUID roleId) {
        return mapper.toResponse(findRoleById(roleId));
    }

    /** {@code name}/{@code description} only — {@code code} and {@code is_system} are immutable. */
    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.ROLE_UPDATED, entityType = "Role", entityIdExpression = "roleId.toString()",
               companyId = "#result?.companyId()")
    public RoleResponse updateRole(UUID roleId, RoleUpdateRequest request) {
        Role role = findRoleById(roleId);
        if (request.name() != null) {
            role.setName(request.name().trim());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        return mapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.ROLE_ACTIVATED, entityType = "Role", entityIdExpression = "roleId.toString()",
               companyId = "#result?.companyId()")
    public RoleResponse activateRole(UUID roleId) {
        Role role = findRoleById(roleId);
        role.setStatus(RoleStatus.ACTIVE);
        return mapper.toResponse(roleRepository.save(role));
    }

    /**
     * {@code is_system} roles (ADMIN/MANAGER/OPERATOR) can never be deactivated, by anyone —
     * protects the seeded roles the whole permission matrix (V41) is built on from being disabled by
     * mistake. Blocking here alone is enough: a system role can never reach {@code INACTIVE} in the
     * first place, so there is nothing for {@code activateRole} to guard against.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.ROLE_DEACTIVATED, entityType = "Role", entityIdExpression = "roleId.toString()",
               companyId = "#result?.companyId()")
    public RoleResponse deactivateRole(UUID roleId) {
        Role role = findRoleById(roleId);
        if (role.isSystem()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "System role cannot be deactivated: " + roleId);
        }
        role.setStatus(RoleStatus.INACTIVE);
        return mapper.toResponse(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    public AccessScopeResponse getScope(UUID scopeId) {
        return mapper.toResponse(findScopeById(scopeId));
    }

    /** {@code name}/{@code description} only — {@code code} and {@code scopeType} are immutable. */
    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.SCOPE_UPDATED, entityType = "AccessScope", entityIdExpression = "scopeId.toString()")
    public AccessScopeResponse updateScope(UUID scopeId, AccessScopeUpdateRequest request) {
        AccessScope scope = findScopeById(scopeId);
        if (request.name() != null) {
            scope.setName(request.name().trim());
        }
        if (request.description() != null) {
            scope.setDescription(request.description());
        }
        return mapper.toResponse(accessScopeRepository.save(scope));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.SCOPE_ACTIVATED, entityType = "AccessScope", entityIdExpression = "scopeId.toString()")
    public AccessScopeResponse activateScope(UUID scopeId) {
        AccessScope scope = findScopeById(scopeId);
        scope.setStatus(OrganizationStatus.ACTIVE);
        return mapper.toResponse(accessScopeRepository.save(scope));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.SCOPE_DEACTIVATED, entityType = "AccessScope", entityIdExpression = "scopeId.toString()")
    public AccessScopeResponse deactivateScope(UUID scopeId) {
        AccessScope scope = findScopeById(scopeId);
        scope.setStatus(OrganizationStatus.INACTIVE);
        return mapper.toResponse(accessScopeRepository.save(scope));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    public PageResult<UserRoleAssignmentResponse> listAssignments(UUID userId, UUID roleId, UUID scopeId,
                                                                   Pageable pageable) {
        return PageResult.from(assignmentRepository.search(userId, roleId, scopeId, pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    public PageResult<PermissionResponse> listPermissions(Pageable pageable) {
        return PageResult.from(permissionRepository.findAll(pageable).map(mapper::toResponse));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.PERMISSION_CREATED, entityType = "Permission", entityIdExpression = "permissionId.toString()")
    public PermissionResponse createPermission(PermissionCreateRequest request) {
        String code = normalizeCode(request.code());
        String resource = normalizeToken(request.resource());
        String action = normalizeToken(request.action());

        if (permissionRepository.existsByCode(code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Permission code", code);
        }
        if (permissionRepository.existsByResourceAndAction(resource, action)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Permission", resource + ":" + action);
        }

        Permission permission = Permission.builder()
                .code(code)
                .resource(resource)
                .action(action)
                .description(request.description())
                .status(OrganizationStatus.ACTIVE)
                .build();
        return mapper.toResponse(permissionRepository.save(permission));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    // RbacAuditDescriptorProvider records the role AND the permission, plus metadata.noOp when the
    // role already held it — the event used to name neither participant.
    @Auditable(action = AuditAction.PERMISSION_GRANTED, entityType = "Role",
               entityId = "#roleId",
               changeMode = com.erp.manufacturing.common.audit.model.AuditChangeMode.NONE)
    public void grantPermission(UUID roleId, UUID permissionId) {
        Role role = findActiveRole(roleId);
        Permission permission = findActivePermission(permissionId);

        if (rolePermissionRepository.existsByRoleIdAndPermissionId(role.getRoleId(), permission.getPermissionId())) {
            return;
        }
        rolePermissionRepository.save(RolePermission.builder()
                .roleId(role.getRoleId())
                .permissionId(permission.getPermissionId())
                .build());
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.PERMISSION_REVOKED, entityType = "Role",
               entityId = "#roleId",
               changeMode = com.erp.manufacturing.common.audit.model.AuditChangeMode.NONE)
    public void revokePermission(UUID roleId, UUID permissionId) {
        rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
    }

    /**
     * The permissions granted to one role — the single authoritative source for the admin UI's
     * permission checkboxes. Without it a client can only read the global permission catalogue and
     * would have to guess which entries a role actually holds.
     *
     * <p>Role existence is checked first so an unknown {@code roleId} answers {@code ENTITY_NOT_FOUND}
     * (404) instead of an empty page, which is indistinguishable from "a real role with no grants".
     *
     * <p>Reads roles of any status on purpose: an {@code INACTIVE} role still has to be inspectable
     * before an admin decides whether to reactivate it.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    public PageResult<PermissionResponse> listRolePermissions(UUID roleId, Pageable pageable) {
        findRoleById(roleId);
        return PageResult.from(rolePermissionRepository.findPermissionsByRoleId(roleId, pageable)
                .map(mapper::toResponse));
    }

    /**
     * The resources a non-{@code GLOBAL} scope covers — the read side of
     * {@link #addScopeResource(UUID, AccessScopeResourceRequest)}, which until now could only be
     * written. {@code GLOBAL} scopes legitimately return an empty page: they carry no resource rows,
     * which is exactly what {@link com.erp.manufacturing.module.organization.security.PermissionGuard}
     * relies on when it treats them as unscoped.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    public PageResult<AccessScopeResourceResponse> listScopeResources(UUID scopeId, Pageable pageable) {
        findScopeById(scopeId);
        return PageResult.from(accessScopeResourceRepository.findByScopeId(scopeId, pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    public PageResult<AccessScopeResponse> listScopes(Pageable pageable) {
        return PageResult.from(accessScopeRepository.findAll(pageable).map(mapper::toResponse));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.ACCESS_SCOPE_CREATED, entityType = "AccessScope", entityIdExpression = "scopeId.toString()")
    public AccessScopeResponse createScope(AccessScopeCreateRequest request) {
        String code = normalizeCode(request.code());
        if (accessScopeRepository.existsByCode(code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Access scope code", code);
        }

        AccessScope scope = AccessScope.builder()
                .code(code)
                .name(request.name().trim())
                .scopeType(request.scopeType())
                .description(request.description())
                .status(OrganizationStatus.ACTIVE)
                .build();
        return mapper.toResponse(accessScopeRepository.save(scope));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.ACCESS_SCOPE_RESOURCE_ADDED, entityType = "AccessScopeResource", entityIdExpression = "scopeResourceId.toString()")
    public AccessScopeResourceResponse addScopeResource(UUID scopeId, AccessScopeResourceRequest request) {
        AccessScope scope = findActiveScope(scopeId);
        validateScopeResource(request.resourceType(), request.resourceId());
        validateScopeResourceType(scope.getScopeType(), request.resourceType());

        if (accessScopeResourceRepository.existsByScopeIdAndResourceTypeAndResourceId(
                scope.getScopeId(), request.resourceType(), request.resourceId())) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Access scope resource", request.resourceType() + ":" + request.resourceId());
        }

        AccessScopeResource resource = AccessScopeResource.builder()
                .scopeId(scope.getScopeId())
                .resourceType(request.resourceType())
                .resourceId(request.resourceId())
                .build();
        return mapper.toResponse(accessScopeResourceRepository.save(resource));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.USER_ROLE_SCOPE_ASSIGNED, entityType = "UserRoleAssignment", entityIdExpression = "assignmentId.toString()")
    public UserRoleAssignmentResponse assignRole(UserRoleAssignmentRequest request) {
        if (!userRepository.existsById(request.userId())) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "User", request.userId());
        }
        Role role = findActiveRole(request.roleId());
        AccessScope scope = findActiveScope(request.scopeId());
        validateExpiresAt(request.expiresAt());
        validateAssignmentScope(role, scope);

        assignmentRepository.findByUserIdAndRoleIdAndScopeIdAndStatus(
                request.userId(), role.getRoleId(), scope.getScopeId(), AssignmentStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Active role assignment", existing.getAssignmentId());
                });

        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .userId(request.userId())
                .roleId(role.getRoleId())
                .scopeId(scope.getScopeId())
                .status(AssignmentStatus.ACTIVE)
                .expiresAt(request.expiresAt())
                .build();
        return mapper.toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.USER_ROLE_SCOPE_REVOKED, entityType = "UserRoleAssignment", entityIdExpression = "assignmentId.toString()")
    public UserRoleAssignmentResponse revokeAssignment(UUID assignmentId) {
        UserRoleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "User role assignment", assignmentId));
        assignment.deactivate();
        return mapper.toResponse(assignmentRepository.save(assignment));
    }

    /** Result of {@link #resolveMyScopes(UUID)} — the {@code scopes[]}/{@code defaultPlantId} pair
     *  {@code GET /api/auth/v1/me} needs. */
    public record UserAccessScopesResult(List<MyAccessScopeResponse> scopes, UUID defaultPlantId) {}

    /**
     * Builds the caller's {@code scopes[]} for {@code GET /api/auth/v1/me} — every company/plant
     * the user has an active assignment on, with the permissions that apply to each.
     *
     * <p>No {@code @PreAuthorize}: this is self-service info about the caller's own access, not an
     * admin operation like the rest of this service.
     *
     * <p>{@code WAREHOUSE}-scoped rows are deliberately dropped here — the frontend only navigates
     * by company/plant. That permission is not lost: it is still counted in the flat
     * {@code permissions[]} union on {@link com.erp.manufacturing.module.auth.dto.MeResponse},
     * exactly like the access token's {@code roles} claim already reports it.
     */
    @Transactional(readOnly = true)
    public UserAccessScopesResult resolveMyScopes(UUID userId) {
        List<ScopeResourcePermissionRow> rows = assignmentRepository.findActiveScopeResourcePermissionRowsForUser(
                userId, Instant.now(), AssignmentStatus.ACTIVE, RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE, OrganizationStatus.ACTIVE);

        Set<String> globalPermissions = new LinkedHashSet<>();
        Map<UUID, Set<String>> permissionsByPlant = new LinkedHashMap<>();
        Map<UUID, Set<String>> permissionsByCompany = new LinkedHashMap<>();

        for (ScopeResourcePermissionRow row : rows) {
            if (row.scopeType() == null || row.permissionCode() == null) {
                continue;
            }
            if (row.scopeType() == ScopeType.GLOBAL) {
                globalPermissions.add(row.permissionCode());
                continue;
            }
            if (row.resourceType() == null || row.resourceId() == null
                    || !isResourceTypeAllowed(row.scopeType(), row.resourceType())) {
                continue;
            }
            switch (row.resourceType()) {
                case PLANT -> permissionsByPlant
                        .computeIfAbsent(row.resourceId(), id -> new LinkedHashSet<>())
                        .add(row.permissionCode());
                case COMPANY -> permissionsByCompany
                        .computeIfAbsent(row.resourceId(), id -> new LinkedHashSet<>())
                        .add(row.permissionCode());
                case WAREHOUSE -> { /* not surfaced as a navigable scope — see javadoc above */ }
            }
        }

        Map<UUID, Plant> plantsById = plantRepository.findAllById(permissionsByPlant.keySet()).stream()
                .collect(Collectors.toMap(Plant::getPlantId, p -> p));
        Set<UUID> companyIds = new HashSet<>(permissionsByCompany.keySet());
        plantsById.values().forEach(plant -> companyIds.add(plant.getCompany().getCompanyId()));
        Map<UUID, Company> companiesById = companyRepository.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getCompanyId, c -> c));

        List<MyAccessScopeResponse> scopes = new ArrayList<>();
        if (!globalPermissions.isEmpty()) {
            scopes.add(new MyAccessScopeResponse("GLOBAL", null, null, null, null, globalPermissions));
        }
        permissionsByCompany.forEach((companyId, permissions) -> {
            Company company = companiesById.get(companyId);
            if (company != null && company.isActive()) {
                scopes.add(new MyAccessScopeResponse(
                        "COMPANY", companyId, company.getCode(), null, null, permissions));
            }
        });
        permissionsByPlant.forEach((plantId, permissions) -> {
            Plant plant = plantsById.get(plantId);
            if (plant != null && plant.isActive() && plant.getCompany() != null) {
                Company company = companiesById.get(plant.getCompany().getCompanyId());
                if (company != null && company.isActive()) {
                    scopes.add(new MyAccessScopeResponse(
                            "PLANT", company.getCompanyId(), company.getCode(), plantId, plant.getCode(), permissions));
                }
            }
        });

        // B80: sort deterministically before picking defaultPlantId — HashMap/Set iteration order
        // is not stable across calls and a client should not see a different "default" every time.
        scopes.sort(Comparator
                .comparing(MyAccessScopeResponse::companyCode, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(MyAccessScopeResponse::plantCode, Comparator.nullsFirst(Comparator.naturalOrder())));

        UUID defaultPlantId = scopes.stream()
                .map(MyAccessScopeResponse::plantId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new UserAccessScopesResult(scopes, defaultPlantId);
    }

    /** Unlike {@link #findActiveRole}, lifecycle operations must be able to read/reactivate an
     *  already-{@code INACTIVE} role, so this does not reject on status. */
    private Role findRoleById(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Role", roleId));
    }

    /** Unlike {@link #findActiveScope}, lifecycle operations must be able to read/reactivate an
     *  already-{@code INACTIVE} scope, so this does not reject on status. */
    private AccessScope findScopeById(UUID scopeId) {
        return accessScopeRepository.findById(scopeId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Access scope", scopeId));
    }

    private Role findActiveRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Role", roleId));
        if (!role.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Role is inactive: " + roleId);
        }
        return role;
    }

    private Permission findActivePermission(UUID permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Permission", permissionId));
        if (!permission.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Permission is inactive: " + permissionId);
        }
        return permission;
    }

    private AccessScope findActiveScope(UUID scopeId) {
        AccessScope scope = accessScopeRepository.findById(scopeId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Access scope", scopeId));
        if (!scope.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Access scope is inactive: " + scopeId);
        }
        return scope;
    }

    private void validateScopeResource(ScopeResourceType resourceType, UUID resourceId) {
        boolean active = switch (resourceType) {
            case COMPANY -> companyRepository.findById(resourceId).map(Company::isActive).orElse(false);
            case PLANT -> plantRepository.findById(resourceId).map(Plant::isActive).orElse(false);
            case WAREHOUSE -> warehouseRepository.findById(resourceId).map(Warehouse::isActive).orElse(false);
        };
        if (!active) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND,
                    resourceType.name().toLowerCase(Locale.ROOT), resourceId);
        }
    }

    private void validateScopeResourceType(ScopeType scopeType, ScopeResourceType resourceType) {
        if (!isResourceTypeAllowed(scopeType, resourceType)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Resource type " + resourceType + " is not allowed for scope type " + scopeType);
        }
    }

    private boolean isResourceTypeAllowed(ScopeType scopeType, ScopeResourceType resourceType) {
        if (scopeType == null || resourceType == null) {
            return false;
        }
        return switch (scopeType) {
            case GLOBAL -> false;
            case COMPANY -> resourceType == ScopeResourceType.COMPANY;
            case PLANT -> resourceType == ScopeResourceType.PLANT;
            case WAREHOUSE_GROUP -> resourceType == ScopeResourceType.WAREHOUSE;
            case CUSTOM -> true;
        };
    }

    private void validateAssignmentScope(Role role, AccessScope scope) {
        if (role.getCompanyId() != null && scope.getScopeType() == ScopeType.GLOBAL) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "A company-owned role cannot be assigned to a global scope");
        }

        List<AccessScopeResource> resources = accessScopeResourceRepository
                .findByScopeId(scope.getScopeId(), Pageable.unpaged())
                .getContent();

        if (scope.getScopeType() == ScopeType.GLOBAL) {
            if (!resources.isEmpty()) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "A global scope cannot contain resources");
            }
            return;
        }
        if (resources.isEmpty()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.DOCUMENT_HAS_NO_LINES,
                    "A non-global scope must contain at least one resource before assignment");
        }

        resources.forEach(resource -> validateScopeResourceType(
                scope.getScopeType(), resource.getResourceType()));
        Set<UUID> owningCompanyIds = resolveOwningCompanyIds(resources);

        if (role.isSystem() && isReservedAdminCode(role.getCode())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "The system administrator role can only be assigned to a global scope");
        }
        if (role.getCompanyId() != null
                && (owningCompanyIds.isEmpty()
                || owningCompanyIds.stream().anyMatch(id -> !role.getCompanyId().equals(id)))) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "The role and access scope must belong to the same company");
        }
    }

    private Set<UUID> resolveOwningCompanyIds(List<AccessScopeResource> resources) {
        Set<UUID> companyIds = resources.stream()
                .filter(resource -> resource.getResourceType() == ScopeResourceType.COMPANY)
                .map(AccessScopeResource::getResourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> plantIds = resources.stream()
                .filter(resource -> resource.getResourceType() == ScopeResourceType.PLANT)
                .map(AccessScopeResource::getResourceId)
                .collect(Collectors.toSet());
        List<Plant> plants = plantRepository.findAllById(plantIds);
        if (plants.size() != plantIds.size() || plants.stream().anyMatch(plant -> !plant.isActive())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Scope contains an unavailable plant");
        }
        plants.forEach(plant -> companyIds.add(plant.getCompany().getCompanyId()));

        Set<UUID> warehouseIds = resources.stream()
                .filter(resource -> resource.getResourceType() == ScopeResourceType.WAREHOUSE)
                .map(AccessScopeResource::getResourceId)
                .collect(Collectors.toSet());
        List<Warehouse> warehouses = warehouseRepository.findAllById(warehouseIds);
        if (warehouses.size() != warehouseIds.size()
                || warehouses.stream().anyMatch(warehouse -> !warehouse.isActive())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Scope contains an unavailable warehouse");
        }
        warehouses.forEach(warehouse -> companyIds.add(
                warehouse.getPlant().getCompany().getCompanyId()));
        return companyIds;
    }

    private void ensureActiveCompany(UUID companyId) {
        boolean active = companyRepository.findById(companyId).map(Company::isActive).orElse(false);
        if (!active) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Company", companyId);
        }
    }

    private void validateExpiresAt(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Assignment expiration must be in the future");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isReservedAdminCode(String code) {
        String normalized = normalizeCode(code);
        return normalized.equals("ADMIN") || normalized.equals("ROLE_ADMIN");
    }

    private String normalizeToken(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
