package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.module.organization.domain.AccessScope;
import com.erp.manufacturing.module.organization.domain.AccessScopeResource;
import com.erp.manufacturing.module.organization.domain.AssignmentStatus;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.ScopeType;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.organization.dto.AccessScopeResourceRequest;
import com.erp.manufacturing.module.organization.dto.MyAccessScopeResponse;
import com.erp.manufacturing.module.organization.dto.RoleCreateRequest;
import com.erp.manufacturing.module.organization.dto.ScopeResourcePermissionRow;
import com.erp.manufacturing.module.organization.dto.UserRoleAssignmentRequest;
import com.erp.manufacturing.module.organization.mapper.OrganizationMapper;
import com.erp.manufacturing.module.organization.repository.AccessScopeRepository;
import com.erp.manufacturing.module.organization.repository.AccessScopeResourceRepository;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PermissionRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.RolePermissionRepository;
import com.erp.manufacturing.module.organization.repository.RoleRepository;
import com.erp.manufacturing.module.organization.repository.UserRoleAssignmentRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.user.repository.UserRepository;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fail-closed invariants for the shape of an access scope and its tenant ownership.
 *
 * <p>Negative cases are intentionally RED against the vulnerable implementation. They become the
 * acceptance gate for the scope validator introduced by the remediation plan.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Access-scope invariant security")
class AccessScopeInvariantSecurityTest {

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
                roleRepository, userRepository, companyRepository, plantRepository, warehouseRepository,
                permissionRepository, rolePermissionRepository, accessScopeRepository,
                accessScopeResourceRepository, assignmentRepository, new OrganizationMapper());
    }

    @Test
    void createCompanyRole_reservedAdminCodeIsRejected() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company(companyId)));

        assertRejectedWith(BusinessErrorCode.OPERATION_NOT_ALLOWED, () -> service.createRole(
                new RoleCreateRequest(companyId, "ADMIN", "Local administrator", null)));

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void createCompanyRole_prefixedReservedAdminCodeIsRejected() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company(companyId)));

        assertRejectedWith(BusinessErrorCode.OPERATION_NOT_ALLOWED, () -> service.createRole(
                new RoleCreateRequest(companyId, "ROLE_ADMIN", "Local administrator", null)));

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void globalScope_cannotContainAnyResource() {
        UUID scopeId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        stubScope(scopeId, ScopeType.GLOBAL);
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant(plantId, UUID.randomUUID())));

        assertRejectedWith(BusinessErrorCode.OPERATION_NOT_ALLOWED, () -> service.addScopeResource(
                scopeId, new AccessScopeResourceRequest(ScopeResourceType.PLANT, plantId)));

        verify(accessScopeResourceRepository, never()).save(any(AccessScopeResource.class));
    }

    @Test
    void companyScope_acceptsOnlyCompanyResources() {
        UUID scopeId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        stubScope(scopeId, ScopeType.COMPANY);
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant(plantId, UUID.randomUUID())));

        assertRejectedWith(BusinessErrorCode.OPERATION_NOT_ALLOWED, () -> service.addScopeResource(
                scopeId, new AccessScopeResourceRequest(ScopeResourceType.PLANT, plantId)));

        verify(accessScopeResourceRepository, never()).save(any(AccessScopeResource.class));
    }

    @Test
    void plantScope_acceptsOnlyPlantResources() {
        UUID scopeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        stubScope(scopeId, ScopeType.PLANT);
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, UUID.randomUUID(), UUID.randomUUID())));

        assertRejectedWith(BusinessErrorCode.OPERATION_NOT_ALLOWED, () -> service.addScopeResource(
                scopeId, new AccessScopeResourceRequest(ScopeResourceType.WAREHOUSE, warehouseId)));

        verify(accessScopeResourceRepository, never()).save(any(AccessScopeResource.class));
    }

    @Test
    void warehouseGroupScope_acceptsOnlyWarehouseResources() {
        UUID scopeId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        stubScope(scopeId, ScopeType.WAREHOUSE_GROUP);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company(companyId)));

        assertRejectedWith(BusinessErrorCode.OPERATION_NOT_ALLOWED, () -> service.addScopeResource(
                scopeId, new AccessScopeResourceRequest(ScopeResourceType.COMPANY, companyId)));

        verify(accessScopeResourceRepository, never()).save(any(AccessScopeResource.class));
    }

    @Test
    void customScope_mayContainMixedExplicitResources() {
        UUID scopeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        stubScope(scopeId, ScopeType.CUSTOM);
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, UUID.randomUUID(), UUID.randomUUID())));
        stubResourceSave();

        service.addScopeResource(
                scopeId, new AccessScopeResourceRequest(ScopeResourceType.WAREHOUSE, warehouseId));

        verify(accessScopeResourceRepository).save(any(AccessScopeResource.class));
    }

    @Test
    void companyOwnedRole_cannotBeAssignedToGlobalScope() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        stubAssignmentBase(userId, role(roleId, companyId), scope(scopeId, ScopeType.GLOBAL));

        assertRejectedWith(BusinessErrorCode.OPERATION_NOT_ALLOWED, () -> service.assignRole(
                new UserRoleAssignmentRequest(userId, roleId, scopeId, null)));

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void systemAdminRole_cannotBeAssignedToPlantScope() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        Role admin = Role.builder()
                .roleId(roleId)
                .companyId(null)
                .code("ADMIN")
                .name("ADMIN")
                .system(true)
                .status(RoleStatus.ACTIVE)
                .build();
        stubAssignmentBase(userId, admin, scope(scopeId, ScopeType.PLANT));
        when(accessScopeResourceRepository.findByScopeId(eq(scopeId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resource(scopeId, ScopeResourceType.PLANT, plantId))));
        when(plantRepository.findAllById(any())).thenReturn(List.of(plant(plantId, companyId)));

        assertRejectedWith(BusinessErrorCode.OPERATION_NOT_ALLOWED, () -> service.assignRole(
                new UserRoleAssignmentRequest(userId, roleId, scopeId, null)));

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void nonGlobalScope_withoutResourcesCannotReceiveAssignment() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        stubAssignmentBase(userId, role(roleId, null), scope(scopeId, ScopeType.PLANT));
        when(accessScopeResourceRepository.findByScopeId(eq(scopeId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertRejectedWith(BusinessErrorCode.DOCUMENT_HAS_NO_LINES, () -> service.assignRole(
                new UserRoleAssignmentRequest(userId, roleId, scopeId, null)));

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void companyOwnedRole_cannotBeAssignedToAnotherCompanyScope() {
        UUID userId = UUID.randomUUID();
        UUID roleCompanyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        stubAssignmentBase(userId, role(roleId, roleCompanyId), scope(scopeId, ScopeType.COMPANY));
        when(accessScopeResourceRepository.findByScopeId(eq(scopeId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resource(scopeId, ScopeResourceType.COMPANY, otherCompanyId))));

        assertRejectedWith(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH, () -> service.assignRole(
                new UserRoleAssignmentRequest(userId, roleId, scopeId, null)));

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void authMe_nonGlobalRowWithoutResourceFailsClosedInsteadOfBecomingGlobal() {
        UUID userId = UUID.randomUUID();
        stubScopeRows(userId, List.of(
                new ScopeResourcePermissionRow(ScopeType.PLANT, null, null, "PERM_WORK_ORDER_READ")));

        assertThat(service.resolveMyScopes(userId).scopes()).isEmpty();
    }

    @Test
    void authMe_globalScopeIsClassifiedByScopeTypeEvenWhenLegacyDataContainsResource() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        stubScopeRows(userId, List.of(
                new ScopeResourcePermissionRow(
                        ScopeType.GLOBAL, ScopeResourceType.PLANT, plantId, "PERM_ACCESS_MANAGE")));
        when(plantRepository.findAllById(any())).thenReturn(List.of(plant(plantId, companyId)));
        when(companyRepository.findAllById(any())).thenReturn(List.of(company(companyId)));

        List<MyAccessScopeResponse> scopes = service.resolveMyScopes(userId).scopes();

        assertThat(scopes).singleElement().satisfies(result -> {
            assertThat(result.scopeType()).isEqualTo("GLOBAL");
            assertThat(result.companyId()).isNull();
            assertThat(result.plantId()).isNull();
            assertThat(result.permissions()).containsExactly("PERM_ACCESS_MANAGE");
        });
    }

    @Test
    void authMe_mismatchedScopeTypeAndResourceTypeFailsClosed() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        stubScopeRows(userId, List.of(
                new ScopeResourcePermissionRow(
                        ScopeType.COMPANY, ScopeResourceType.PLANT, plantId, "PERM_ORG_READ")));
        when(plantRepository.findAllById(any())).thenReturn(List.of(plant(plantId, companyId)));
        when(companyRepository.findAllById(any())).thenReturn(List.of(company(companyId)));

        assertThat(service.resolveMyScopes(userId).scopes()).isEmpty();
    }

    @Test
    void authMe_validPlantScopeRemainsVisible() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        stubScopeRows(userId, List.of(
                new ScopeResourcePermissionRow(
                        ScopeType.PLANT, ScopeResourceType.PLANT, plantId, "PERM_ORG_READ")));
        when(plantRepository.findAllById(any())).thenReturn(List.of(plant(plantId, companyId)));
        when(companyRepository.findAllById(any())).thenReturn(List.of(company(companyId)));

        assertThat(service.resolveMyScopes(userId).scopes())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.scopeType()).isEqualTo("PLANT");
                    assertThat(result.companyId()).isEqualTo(companyId);
                    assertThat(result.plantId()).isEqualTo(plantId);
                });
    }

    /**
     * EH-2 split {@code OPERATION_NOT_ALLOWED} into three, and these invariants no longer all land on
     * the same one — "the scope has no resources yet" and "the role belongs to another company" are
     * now distinct codes. The expected code is therefore passed in per call site rather than baked
     * into the helper: a shared helper asserting one code would have quietly stopped distinguishing
     * the invariants it exists to protect.
     */
    private void assertRejectedWith(ErrorCode expected, ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).getErrorCode())
                        .isEqualTo(expected));
    }

    private void stubScope(UUID scopeId, ScopeType scopeType) {
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(scope(scopeId, scopeType)));
    }

    private void stubResourceSave() {
        when(accessScopeResourceRepository.save(any(AccessScopeResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubAssignmentBase(UUID userId, Role role, AccessScope scope) {
        when(userRepository.existsById(userId)).thenReturn(true);
        when(roleRepository.findById(role.getRoleId())).thenReturn(Optional.of(role));
        when(accessScopeRepository.findById(scope.getScopeId())).thenReturn(Optional.of(scope));
    }

    private void stubScopeRows(UUID userId, List<ScopeResourcePermissionRow> rows) {
        when(assignmentRepository.findActiveScopeResourcePermissionRowsForUser(
                eq(userId), any(), eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(rows);
    }

    private AccessScope scope(UUID scopeId, ScopeType type) {
        return AccessScope.builder()
                .scopeId(scopeId)
                .code("SCOPE-" + scopeId)
                .name("Scope")
                .scopeType(type)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private AccessScopeResource resource(UUID scopeId, ScopeResourceType type, UUID resourceId) {
        return AccessScopeResource.builder()
                .scopeResourceId(UUID.randomUUID())
                .scopeId(scopeId)
                .resourceType(type)
                .resourceId(resourceId)
                .build();
    }

    private Role role(UUID roleId, UUID companyId) {
        return Role.builder()
                .roleId(roleId)
                .companyId(companyId)
                .code("PLANNER")
                .name("Planner")
                .system(false)
                .status(RoleStatus.ACTIVE)
                .build();
    }

    private Company company(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("CO-" + companyId.toString().substring(0, 8))
                .name("Company")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Plant plant(UUID plantId, UUID companyId) {
        return Plant.builder()
                .plantId(plantId)
                .company(company(companyId))
                .code("PL-" + plantId.toString().substring(0, 8))
                .name("Plant")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, UUID plantId, UUID companyId) {
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant(plantId, companyId))
                .code("WH-" + warehouseId.toString().substring(0, 8))
                .name("Warehouse")
                .type(WarehouseType.GENERAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
