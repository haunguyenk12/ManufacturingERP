package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.dto.*;
import com.erp.manufacturing.module.organization.mapper.OrganizationMapper;
import com.erp.manufacturing.module.organization.repository.*;
import com.erp.manufacturing.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlService tests")
class AccessControlServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock PlantRepository plantRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock RolePermissionRepository rolePermissionRepository;
    @Mock AccessScopeRepository accessScopeRepository;
    @Mock AccessScopeResourceRepository accessScopeResourceRepository;
    @Mock UserRoleAssignmentRepository assignmentRepository;

    AccessControlService service;

    @BeforeEach
    void setUp() {
        service = new AccessControlService(
                roleRepository,
                userRepository,
                companyRepository,
                plantRepository,
                warehouseRepository,
                permissionRepository,
                rolePermissionRepository,
                accessScopeRepository,
                accessScopeResourceRepository,
                assignmentRepository,
                new OrganizationMapper());
    }

    @Test
    void createRole_companyScoped_success() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));
        when(roleRepository.existsByCodeAndCompanyId("PLANNER", companyId)).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createRole(new RoleCreateRequest(companyId, "planner", "Planner", null));

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo(companyId);
        assertThat(captor.getValue().getCode()).isEqualTo("PLANNER");
        assertThat(captor.getValue().isSystem()).isFalse();
    }

    @Test
    void createPermission_duplicateCode_fails() {
        when(permissionRepository.existsByCode("ITEM:READ")).thenReturn(true);

        assertThatThrownBy(() -> service.createPermission(
                new PermissionCreateRequest("ITEM:READ", "ITEM", "READ", null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));
    }

    @Test
    void addScopeResource_validatesPolymorphicResource() {
        UUID scopeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(activeScope(scopeId)));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(accessScopeResourceRepository.existsByScopeIdAndResourceTypeAndResourceId(
                scopeId, ScopeResourceType.WAREHOUSE, warehouseId)).thenReturn(false);
        when(accessScopeResourceRepository.save(any(AccessScopeResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.addScopeResource(scopeId,
                new AccessScopeResourceRequest(ScopeResourceType.WAREHOUSE, warehouseId));

        verify(accessScopeResourceRepository).save(any(AccessScopeResource.class));
    }

    @Test
    void assignRole_success() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(activeRole(roleId)));
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(activeScope(scopeId)));
        when(assignmentRepository.findByUserIdAndRoleIdAndScopeIdAndStatus(
                userId, roleId, scopeId, AssignmentStatus.ACTIVE)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserRoleAssignmentResponse response = service.assignRole(
                new UserRoleAssignmentRequest(userId, roleId, scopeId, Instant.now().plusSeconds(3600)));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.roleId()).isEqualTo(roleId);
        assertThat(response.scopeId()).isEqualTo(scopeId);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void assignRole_duplicateActiveAssignment_fails() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(activeRole(roleId)));
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(activeScope(scopeId)));
        when(assignmentRepository.findByUserIdAndRoleIdAndScopeIdAndStatus(
                userId, roleId, scopeId, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(UserRoleAssignment.builder()
                        .assignmentId(UUID.randomUUID())
                        .userId(userId)
                        .roleId(roleId)
                        .scopeId(scopeId)
                        .status(AssignmentStatus.ACTIVE)
                        .build()));

        assertThatThrownBy(() -> service.assignRole(
                new UserRoleAssignmentRequest(userId, roleId, scopeId, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));
    }

    @Test
    void revokeAssignment_softDeletesOnly() {
        UUID assignmentId = UUID.randomUUID();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .assignmentId(assignmentId)
                .userId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
                .scopeId(UUID.randomUUID())
                .status(AssignmentStatus.ACTIVE)
                .build();
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(assignment)).thenReturn(assignment);

        UserRoleAssignmentResponse response = service.revokeAssignment(assignmentId);

        assertThat(response.status()).isEqualTo("INACTIVE");
        verify(assignmentRepository).save(assignment);
        verify(assignmentRepository, never()).delete(any());
    }

    private Company activeCompany(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Warehouse activeWarehouse(UUID warehouseId) {
        Plant plant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(activeCompany(UUID.randomUUID()))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant)
                .code("RM")
                .name("Raw")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Role activeRole(UUID roleId) {
        return Role.builder()
                .roleId(roleId)
                .code("PLANNER")
                .name("Planner")
                .status(RoleStatus.ACTIVE)
                .build();
    }

    private AccessScope activeScope(UUID scopeId) {
        return AccessScope.builder()
                .scopeId(scopeId)
                .code("SCOPE")
                .name("Scope")
                .scopeType(ScopeType.CUSTOM)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
