package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.organization.domain.AccessScope;
import com.erp.manufacturing.module.organization.domain.AccessScopeResource;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.ScopeType;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.organization.dto.AccessScopeResourceRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Exhaustive ScopeType × ResourceType acceptance matrix. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Access-scope authorization matrix")
class AccessScopeAuthorizationMatrixSecurityTest {

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

    @ParameterizedTest(name = "{0} + {1} => allowed={2}")
    @MethodSource("scopeResourceMatrix")
    void everyScopeResourceCombination_isEnforced(
            ScopeType scopeType, ScopeResourceType resourceType, boolean allowed) {
        UUID scopeId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        lenient().when(accessScopeRepository.findById(scopeId))
                .thenReturn(Optional.of(scope(scopeId, scopeType)));
        lenient().when(companyRepository.findById(resourceId))
                .thenReturn(Optional.of(company(resourceId)));
        lenient().when(plantRepository.findById(resourceId))
                .thenReturn(Optional.of(plant(resourceId)));
        lenient().when(warehouseRepository.findById(resourceId))
                .thenReturn(Optional.of(warehouse(resourceId)));
        lenient().when(accessScopeResourceRepository.save(any(AccessScopeResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccessScopeResourceRequest request = new AccessScopeResourceRequest(resourceType, resourceId);
        if (allowed) {
            assertThatCode(() -> service.addScopeResource(scopeId, request)).doesNotThrowAnyException();
            verify(accessScopeResourceRepository).save(any(AccessScopeResource.class));
        } else {
            assertThatThrownBy(() -> service.addScopeResource(scopeId, request))
                    .isInstanceOf(AppException.class)
                    .satisfies(error -> assertThat(((AppException) error).getErrorCode())
                            .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
            verify(accessScopeResourceRepository, never()).save(any(AccessScopeResource.class));
        }
    }

    static Stream<Arguments> scopeResourceMatrix() {
        return Stream.of(ScopeType.values())
                .flatMap(scopeType -> Stream.of(ScopeResourceType.values())
                        .map(resourceType -> Arguments.of(
                                scopeType, resourceType, isAllowed(scopeType, resourceType))));
    }

    private static boolean isAllowed(ScopeType scopeType, ScopeResourceType resourceType) {
        return switch (scopeType) {
            case GLOBAL -> false;
            case COMPANY -> resourceType == ScopeResourceType.COMPANY;
            case PLANT -> resourceType == ScopeResourceType.PLANT;
            case WAREHOUSE_GROUP -> resourceType == ScopeResourceType.WAREHOUSE;
            case CUSTOM -> true;
        };
    }

    private AccessScope scope(UUID scopeId, ScopeType type) {
        return AccessScope.builder()
                .scopeId(scopeId)
                .code("SCOPE")
                .name("Scope")
                .scopeType(type)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Company company(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("COMPANY")
                .name("Company")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Plant plant(UUID plantId) {
        return Plant.builder()
                .plantId(plantId)
                .company(company(UUID.randomUUID()))
                .code("PLANT")
                .name("Plant")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId) {
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant(UUID.randomUUID()))
                .code("WAREHOUSE")
                .name("Warehouse")
                .type(WarehouseType.GENERAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
