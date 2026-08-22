package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.dto.*;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.mapper.OrganizationMapper;
import com.erp.manufacturing.module.organization.repository.*;
import com.erp.manufacturing.module.user.repository.UserRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
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

    // ── role lifecycle (C2-4) ─────────────────────────────────────────────

    @Test
    void updateRole_changesNameAndDescriptionOnly() {
        UUID roleId = UUID.randomUUID();
        Role role = Role.builder().roleId(roleId).code("PLANNER").name("Planner")
                .description("old").system(false).status(RoleStatus.ACTIVE).build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = service.updateRole(roleId, new RoleUpdateRequest("New Name", "new desc"));

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.description()).isEqualTo("new desc");
        assertThat(response.code()).isEqualTo("PLANNER");
    }

    @Test
    void updateRole_nullFields_keepExistingValues() {
        UUID roleId = UUID.randomUUID();
        Role role = Role.builder().roleId(roleId).code("PLANNER").name("Planner")
                .description("old").system(false).status(RoleStatus.ACTIVE).build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = service.updateRole(roleId, new RoleUpdateRequest(null, null));

        assertThat(response.name()).isEqualTo("Planner");
        assertThat(response.description()).isEqualTo("old");
    }

    @Test
    void deactivateRole_systemRole_throwsOperationNotAllowedBeforeSaving() {
        UUID roleId = UUID.randomUUID();
        Role role = Role.builder().roleId(roleId).code("ADMIN").name("Admin")
                .system(true).status(RoleStatus.ACTIVE).build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.deactivateRole(roleId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(roleRepository, never()).save(any());
    }

    @Test
    void deactivateRole_customRole_succeeds() {
        UUID roleId = UUID.randomUUID();
        Role role = Role.builder().roleId(roleId).code("PLANNER").name("Planner")
                .system(false).status(RoleStatus.ACTIVE).build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = service.deactivateRole(roleId);

        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void activateRole_setsStatusActive() {
        UUID roleId = UUID.randomUUID();
        Role role = Role.builder().roleId(roleId).code("PLANNER").name("Planner")
                .system(false).status(RoleStatus.INACTIVE).build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = service.activateRole(roleId);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    // ── scope lifecycle (C2-4) ────────────────────────────────────────────

    @Test
    void updateScope_changesNameAndDescriptionOnly() {
        UUID scopeId = UUID.randomUUID();
        AccessScope scope = AccessScope.builder().scopeId(scopeId).code("SCOPE").name("Scope")
                .scopeType(ScopeType.CUSTOM).description("old").status(OrganizationStatus.ACTIVE).build();
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(scope));
        when(accessScopeRepository.save(any(AccessScope.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessScopeResponse response = service.updateScope(scopeId, new AccessScopeUpdateRequest("New Scope", "new desc"));

        assertThat(response.name()).isEqualTo("New Scope");
        assertThat(response.description()).isEqualTo("new desc");
        assertThat(response.code()).isEqualTo("SCOPE");
    }

    @Test
    void deactivateScope_setsStatusInactive() {
        UUID scopeId = UUID.randomUUID();
        AccessScope scope = AccessScope.builder().scopeId(scopeId).code("SCOPE").name("Scope")
                .scopeType(ScopeType.CUSTOM).status(OrganizationStatus.ACTIVE).build();
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(scope));
        when(accessScopeRepository.save(any(AccessScope.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessScopeResponse response = service.deactivateScope(scopeId);

        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void activateScope_setsStatusActive() {
        UUID scopeId = UUID.randomUUID();
        AccessScope scope = AccessScope.builder().scopeId(scopeId).code("SCOPE").name("Scope")
                .scopeType(ScopeType.CUSTOM).status(OrganizationStatus.INACTIVE).build();
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(scope));
        when(accessScopeRepository.save(any(AccessScope.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessScopeResponse response = service.activateScope(scopeId);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    // ── listAssignments (C2-4) ────────────────────────────────────────────

    @Test
    void listAssignments_filtersByUserIdOnly() {
        UUID userId = UUID.randomUUID();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(assignmentRepository.search(userId, null, null, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.listAssignments(userId, null, null, pageable);

        verify(assignmentRepository).search(userId, null, null, pageable);
    }

    @Test
    void listAssignments_filtersByRoleIdOnly() {
        UUID roleId = UUID.randomUUID();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(assignmentRepository.search(null, roleId, null, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.listAssignments(null, roleId, null, pageable);

        verify(assignmentRepository).search(null, roleId, null, pageable);
    }

    @Test
    void listAssignments_filtersByAllThreeParameters() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(assignmentRepository.search(userId, roleId, scopeId, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.listAssignments(userId, roleId, scopeId, pageable);

        verify(assignmentRepository).search(userId, roleId, scopeId, pageable);
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
        when(accessScopeResourceRepository.findByScopeId(scopeId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(AccessScopeResource.builder()
                        .scopeId(scopeId)
                        .resourceType(ScopeResourceType.COMPANY)
                        .resourceId(UUID.randomUUID())
                        .build())));
        when(assignmentRepository.findByUserIdAndRoleIdAndScopeIdAndStatus(
                userId, roleId, scopeId, AssignmentStatus.ACTIVE)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant expiresAt = Instant.now().plusSeconds(3600);
        UserRoleAssignmentResponse response = service.assignRole(
                new UserRoleAssignmentRequest(userId, roleId, scopeId, expiresAt));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.roleId()).isEqualTo(roleId);
        assertThat(response.scopeId()).isEqualTo(scopeId);
        assertThat(response.status()).isEqualTo("ACTIVE");

        // The frontend reported a lost expiry on 2026-08-13 (root cause was JSON serialisation, not
        // this layer — CLAUDE.md §0.42). This case already passed an expiry but asserted nothing
        // about it, so a service or mapper that quietly dropped the field would have stayed green.
        ArgumentCaptor<UserRoleAssignment> saved = ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(assignmentRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void assignRole_duplicateActiveAssignment_fails() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(activeRole(roleId)));
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(activeScope(scopeId)));
        when(accessScopeResourceRepository.findByScopeId(scopeId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(AccessScopeResource.builder()
                        .scopeId(scopeId)
                        .resourceType(ScopeResourceType.COMPANY)
                        .resourceId(UUID.randomUUID())
                        .build())));
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

    // ── resolveMyScopes (GET /api/auth/v1/me) ───────────────────────────────

    @Test
    @DisplayName("resolveMyScopes: two scopes granting different permissions on the same plant merge into one entry")
    void resolveMyScopes_mergesPermissions_forSameResourceFromMultipleScopes() {
        UUID userId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        stubAssignmentRows(userId,
                row(ScopeType.PLANT, ScopeResourceType.PLANT, plantId, "PERM_WORK_ORDER_MANAGE"),
                row(ScopeType.PLANT, ScopeResourceType.PLANT, plantId, "PERM_MRP_RUN"));
        stubPlantsAndCompanies(List.of(plant(plantId, "PL-HN", companyId, "CO-01")), List.of());

        AccessControlService.UserAccessScopesResult result = service.resolveMyScopes(userId);

        assertThat(result.scopes()).hasSize(1);
        assertThat(result.scopes().get(0).permissions())
                .containsExactlyInAnyOrder("PERM_WORK_ORDER_MANAGE", "PERM_MRP_RUN");
    }

    @Test
    @DisplayName("resolveMyScopes: WAREHOUSE-scoped rows are dropped from scopes[] entirely")
    void resolveMyScopes_excludesWarehouseResourceType_fromScopes() {
        UUID userId = UUID.randomUUID();
        stubAssignmentRows(userId,
                row(ScopeType.CUSTOM, ScopeResourceType.WAREHOUSE, UUID.randomUUID(), "PERM_INVENTORY_MOVE"));
        stubPlantsAndCompanies(List.of(), List.of());

        AccessControlService.UserAccessScopesResult result = service.resolveMyScopes(userId);

        assertThat(result.scopes()).isEmpty();
    }

    @Test
    @DisplayName("resolveMyScopes: a GLOBAL row (no resource) surfaces with null companyId/plantId")
    void resolveMyScopes_globalRows_surfacedWithNullCompanyAndPlant() {
        UUID userId = UUID.randomUUID();
        stubAssignmentRows(userId, row(ScopeType.GLOBAL, null, null, "PERM_ACCESS_MANAGE"));
        stubPlantsAndCompanies(List.of(), List.of());

        AccessControlService.UserAccessScopesResult result = service.resolveMyScopes(userId);

        assertThat(result.scopes()).hasSize(1);
        MyAccessScopeResponse global = result.scopes().get(0);
        assertThat(global.scopeType()).isEqualTo("GLOBAL");
        assertThat(global.companyId()).isNull();
        assertThat(global.plantId()).isNull();
        assertThat(global.permissions()).containsExactly("PERM_ACCESS_MANAGE");
    }

    @Test
    @DisplayName("resolveMyScopes: defaultPlantId is the same regardless of the order rows are returned in")
    void resolveMyScopes_defaultPlantId_isDeterministic_regardlessOfInputOrder() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantA = UUID.randomUUID();
        UUID plantB = UUID.randomUUID();
        List<Plant> plants = List.of(
                plant(plantA, "PL-B", companyId, "CO-01"),   // code "PL-B" — deliberately NOT first alphabetically
                plant(plantB, "PL-A", companyId, "CO-01"));

        stubAssignmentRows(userId,
                row(ScopeType.PLANT, ScopeResourceType.PLANT, plantA, "PERM_WORK_ORDER_READ"),
                row(ScopeType.PLANT, ScopeResourceType.PLANT, plantB, "PERM_WORK_ORDER_READ"));
        stubPlantsAndCompanies(plants, List.of());
        UUID firstOrderDefault = service.resolveMyScopes(userId).defaultPlantId();

        stubAssignmentRows(userId,
                row(ScopeType.PLANT, ScopeResourceType.PLANT, plantB, "PERM_WORK_ORDER_READ"),
                row(ScopeType.PLANT, ScopeResourceType.PLANT, plantA, "PERM_WORK_ORDER_READ"));
        stubPlantsAndCompanies(plants, List.of());
        UUID secondOrderDefault = service.resolveMyScopes(userId).defaultPlantId();

        assertThat(firstOrderDefault).isEqualTo(secondOrderDefault).isEqualTo(plantB); // "PL-A" sorts first
    }

    @Test
    @DisplayName("resolveMyScopes: defaultPlantId is null when the user has no PLANT-type scope")
    void resolveMyScopes_defaultPlantId_null_whenNoPlantScope() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        stubAssignmentRows(userId,
                row(ScopeType.COMPANY, ScopeResourceType.COMPANY, companyId, "PERM_WORK_ORDER_READ"));
        stubPlantsAndCompanies(List.of(), List.of(company(companyId, "CO-01")));

        assertThat(service.resolveMyScopes(userId).defaultPlantId()).isNull();
    }

    @Test
    @DisplayName("resolveMyScopes: batch-loads plants/companies once, never per-row findById (rule C14)")
    void resolveMyScopes_batchLoadsPlantsAndCompanies_noPerRowFindById() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        stubAssignmentRows(userId,
                row(ScopeType.PLANT, ScopeResourceType.PLANT, plantId, "PERM_WORK_ORDER_READ"));
        stubPlantsAndCompanies(List.of(plant(plantId, "PL-HN", companyId, "CO-01")), List.of());

        service.resolveMyScopes(userId);

        verify(plantRepository).findAllById(any());
        verify(plantRepository, never()).findById(any());
        verify(companyRepository).findAllById(any());
        verify(companyRepository, never()).findById(any());
    }

    @Test
    @DisplayName("resolveMyScopes: COMPANY scope resolves companyCode with plantId/plantCode left null")
    void resolveMyScopes_companyScope_resolvesCompanyCodeWithNullPlant() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        stubAssignmentRows(userId,
                row(ScopeType.COMPANY, ScopeResourceType.COMPANY, companyId, "PERM_ORG_READ"));
        stubPlantsAndCompanies(List.of(), List.of(company(companyId, "CO-01")));

        MyAccessScopeResponse scope = service.resolveMyScopes(userId).scopes().get(0);

        assertThat(scope.scopeType()).isEqualTo("COMPANY");
        assertThat(scope.companyId()).isEqualTo(companyId);
        assertThat(scope.companyCode()).isEqualTo("CO-01");
        assertThat(scope.plantId()).isNull();
        assertThat(scope.plantCode()).isNull();
    }

    @Test
    @DisplayName("resolveMyScopes: PLANT scope resolves both its own code and its parent company's code")
    void resolveMyScopes_plantScope_resolvesCompanyAndPlantCode() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        stubAssignmentRows(userId,
                row(ScopeType.PLANT, ScopeResourceType.PLANT, plantId, "PERM_WORK_ORDER_MANAGE"));
        stubPlantsAndCompanies(List.of(plant(plantId, "PL-HN", companyId, "CO-01")), List.of());

        MyAccessScopeResponse scope = service.resolveMyScopes(userId).scopes().get(0);

        assertThat(scope.scopeType()).isEqualTo("PLANT");
        assertThat(scope.companyId()).isEqualTo(companyId);
        assertThat(scope.companyCode()).isEqualTo("CO-01");
        assertThat(scope.plantId()).isEqualTo(plantId);
        assertThat(scope.plantCode()).isEqualTo("PL-HN");
    }

    // ── resolveMyScopes test helpers ─────────────────────────────────────────

    private void stubAssignmentRows(UUID userId, ScopeResourcePermissionRow... rows) {
        when(assignmentRepository.findActiveScopeResourcePermissionRowsForUser(
                eq(userId), any(), eq(AssignmentStatus.ACTIVE), eq(RoleStatus.ACTIVE),
                eq(OrganizationStatus.ACTIVE), eq(OrganizationStatus.ACTIVE)))
                .thenReturn(List.of(rows));
    }

    private void stubPlantsAndCompanies(List<Plant> plants, List<Company> companies) {
        // Every plant's parent company must also be resolvable, exactly like resolveMyScopes()
        // itself unions plant-derived company ids into the company batch lookup. Deduplicated by
        // companyId (not object identity) — two plants sharing a company must not produce two
        // Company rows with the same id, which would blow up the real code's
        // Collectors.toMap(Company::getCompanyId, ...).
        java.util.Map<UUID, Company> byId = new java.util.LinkedHashMap<>();
        companies.forEach(c -> byId.put(c.getCompanyId(), c));
        plants.forEach(p -> byId.put(p.getCompany().getCompanyId(), p.getCompany()));
        lenient().when(plantRepository.findAllById(any())).thenReturn(plants);
        lenient().when(companyRepository.findAllById(any())).thenReturn(List.copyOf(byId.values()));
    }

    private ScopeResourcePermissionRow row(ScopeType scopeType, ScopeResourceType resourceType,
                                           UUID resourceId, String permissionCode) {
        return new ScopeResourcePermissionRow(scopeType, resourceType, resourceId, permissionCode);
    }

    private Plant plant(UUID plantId, String plantCode, UUID companyId, String companyCode) {
        return Plant.builder()
                .plantId(plantId)
                .company(company(companyId, companyCode))
                .code(plantCode)
                .name(plantCode)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    // ── role permission / scope resource membership reads (FE RBAC contract) ──

    @Test
    @DisplayName("listRolePermissions: returns the permissions actually granted to the role")
    void listRolePermissions_returnsTheGrantedPermissions() {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        Permission granted = Permission.builder()
                .permissionId(permissionId)
                .code("PERM_INVENTORY_READ")
                .resource("INVENTORY")
                .action("READ")
                .description("Read inventory")
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(activeRole(roleId)));
        when(rolePermissionRepository.findPermissionsByRoleId(eq(roleId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(granted)));

        PageResult<PermissionResponse> result =
                service.listRolePermissions(roleId, PageRequest.of(0, 20));

        assertThat(result.content()).singleElement().satisfies(permission -> {
            assertThat(permission.permissionId()).isEqualTo(permissionId);
            assertThat(permission.code()).isEqualTo("PERM_INVENTORY_READ");
            assertThat(permission.resource()).isEqualTo("INVENTORY");
            assertThat(permission.action()).isEqualTo("READ");
            assertThat(permission.status()).isEqualTo("ACTIVE");
        });
        verify(rolePermissionRepository).findPermissionsByRoleId(eq(roleId), any(Pageable.class));
    }

    @Test
    @DisplayName("listRolePermissions: unknown role is 404, not an empty page")
    void listRolePermissions_unknownRole_throwsBeforeQueryingMembership() {
        UUID roleId = UUID.randomUUID();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listRolePermissions(roleId, PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));

        // An empty page would be indistinguishable from a real role holding no permissions.
        verifyNoInteractions(rolePermissionRepository);
    }

    @Test
    @DisplayName("listScopeResources: returns the resources the scope covers")
    void listScopeResources_returnsTheScopeMembership() {
        UUID scopeId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID scopeResourceId = UUID.randomUUID();
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(activeScope(scopeId)));
        when(accessScopeResourceRepository.findByScopeId(eq(scopeId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(AccessScopeResource.builder()
                        .scopeResourceId(scopeResourceId)
                        .scopeId(scopeId)
                        .resourceType(ScopeResourceType.PLANT)
                        .resourceId(plantId)
                        .build())));

        PageResult<AccessScopeResourceResponse> result =
                service.listScopeResources(scopeId, PageRequest.of(0, 20));

        assertThat(result.content()).singleElement().satisfies(resource -> {
            assertThat(resource.scopeResourceId()).isEqualTo(scopeResourceId);
            assertThat(resource.resourceType()).isEqualTo("PLANT");
            assertThat(resource.resourceId()).isEqualTo(plantId);
        });
    }

    @Test
    @DisplayName("listScopeResources: unknown scope is 404, not an empty page")
    void listScopeResources_unknownScope_throwsBeforeQueryingMembership() {
        UUID scopeId = UUID.randomUUID();
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listScopeResources(scopeId, PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));

        verifyNoInteractions(accessScopeResourceRepository);
    }

    private Company company(UUID companyId, String code) {
        return Company.builder()
                .companyId(companyId)
                .code(code)
                .name(code)
                .status(OrganizationStatus.ACTIVE)
                .build();
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
