package com.erp.manufacturing.module.organization.mapper;

import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.dto.*;
import com.erp.manufacturing.module.organization.domain.Role;
import org.springframework.stereotype.Component;

@Component
public class OrganizationMapper {

    public CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getCompanyId(),
                company.getCode(),
                company.getName(),
                company.getStatus().name(),
                company.getCreatedAt(),
                company.getUpdatedAt());
    }

    public PlantResponse toResponse(Plant plant) {
        return new PlantResponse(
                plant.getPlantId(),
                plant.getCompany().getCompanyId(),
                plant.getCode(),
                plant.getName(),
                plant.getTimezone(),
                plant.getStatus().name(),
                plant.getCreatedAt(),
                plant.getUpdatedAt());
    }

    public WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getWarehouseId(),
                warehouse.getPlant().getPlantId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getType().name(),
                warehouse.getStatus().name(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt());
    }

    public PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(
                permission.getPermissionId(),
                permission.getCode(),
                permission.getResource(),
                permission.getAction(),
                permission.getDescription(),
                permission.getStatus().name());
    }

    public RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getRoleId(),
                role.getCompanyId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isSystem(),
                role.getStatus().name());
    }

    public AccessScopeResponse toResponse(AccessScope scope) {
        return new AccessScopeResponse(
                scope.getScopeId(),
                scope.getCode(),
                scope.getName(),
                scope.getScopeType().name(),
                scope.getDescription(),
                scope.getStatus().name());
    }

    public AccessScopeResourceResponse toResponse(AccessScopeResource resource) {
        return new AccessScopeResourceResponse(
                resource.getScopeResourceId(),
                resource.getScopeId(),
                resource.getResourceType().name(),
                resource.getResourceId());
    }

    public UserRoleAssignmentResponse toResponse(UserRoleAssignment assignment) {
        return new UserRoleAssignmentResponse(
                assignment.getAssignmentId(),
                assignment.getUserId(),
                assignment.getRoleId(),
                assignment.getScopeId(),
                assignment.getStatus().name(),
                assignment.getExpiresAt());
    }
}
