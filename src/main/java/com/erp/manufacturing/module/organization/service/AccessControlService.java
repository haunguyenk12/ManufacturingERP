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
import java.util.Locale;
import java.util.UUID;

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
    @Auditable(action = AuditAction.ROLE_CREATED, entityType = "Role", entityIdExpression = "roleId.toString()")
    public RoleResponse createRole(RoleCreateRequest request) {
        UUID companyId = request.companyId();
        if (companyId != null) {
            ensureActiveCompany(companyId);
        }

        String code = normalizeCode(request.code());
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
    @Auditable(action = AuditAction.PERMISSION_GRANTED, entityType = "Role")
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
    @Auditable(action = AuditAction.PERMISSION_REVOKED, entityType = "Role")
    public void revokePermission(UUID roleId, UUID permissionId) {
        rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
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

    private Role findActiveRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Role", roleId));
        if (!role.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Role is inactive: " + roleId);
        }
        return role;
    }

    private Permission findActivePermission(UUID permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Permission", permissionId));
        if (!permission.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Permission is inactive: " + permissionId);
        }
        return permission;
    }

    private AccessScope findActiveScope(UUID scopeId) {
        AccessScope scope = accessScopeRepository.findById(scopeId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Access scope", scopeId));
        if (!scope.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
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

    private String normalizeToken(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
