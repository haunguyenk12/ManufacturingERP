package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationLookupService tests")
class OrganizationLookupServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock PlantRepository plantRepository;
    @Mock WarehouseRepository warehouseRepository;

    OrganizationLookupService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationLookupService(companyRepository, plantRepository, warehouseRepository);
    }

    @Test
    void resolveCompany_returnsAllCompanyWarehouseIds() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company(companyId)));
        when(warehouseRepository.findByPlantCompanyCompanyId(companyId)).thenReturn(List.of(warehouse(warehouseId, companyId)));

        OrganizationScopeResolution resolution = service.resolveScope(ScopeResourceType.COMPANY, companyId);

        assertThat(resolution.companyId()).isEqualTo(companyId);
        assertThat(resolution.warehouseIds()).containsExactly(warehouseId);
    }

    @Test
    void resolvePlant_returnsPlantCompanyAndWarehouses() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Plant plant = Plant.builder()
                .plantId(plantId)
                .company(company(companyId))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));
        when(warehouseRepository.findByPlantPlantId(plantId)).thenReturn(List.of(warehouse(warehouseId, companyId)));

        OrganizationScopeResolution resolution = service.resolveScope(ScopeResourceType.PLANT, plantId);

        assertThat(resolution.companyId()).isEqualTo(companyId);
        assertThat(resolution.warehouseIds()).containsExactly(warehouseId);
    }

    @Test
    void resolveWarehouse_returnsExactWarehouseOnly() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, companyId)));

        OrganizationScopeResolution resolution = service.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId);

        assertThat(resolution.companyId()).isEqualTo(companyId);
        assertThat(resolution.warehouseIds()).containsExactly(warehouseId);
    }

    /**
     * EH-2: these four guards are the most re-used validation in the codebase — every module reaches
     * master data through them — and until now <b>nothing pinned the code they throw</b>. A mutation
     * that swapped {@code RESOURCE_INACTIVE} for {@code RESOURCE_SCOPE_MISMATCH} here left the whole
     * suite green, which is precisely the hole rule {@code R1} exists to close: both codes are 422, so
     * a status-only assertion elsewhere cannot tell them apart either.
     */
    @Test
    @DisplayName("getActiveCompany: an inactive company is RESOURCE_INACTIVE, not a scope mismatch")
    void getActiveCompany_inactiveCompany_isResourceInactive() {
        UUID companyId = UUID.randomUUID();
        Company inactive = company(companyId);
        inactive.setStatus(OrganizationStatus.INACTIVE);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getActiveCompany(companyId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
    }

    @Test
    @DisplayName("getActivePlant: an inactive plant is RESOURCE_INACTIVE")
    void getActivePlant_inactivePlant_isResourceInactive() {
        UUID plantId = UUID.randomUUID();
        Plant inactive = Plant.builder().plantId(plantId).company(company(UUID.randomUUID()))
                .code("P1").name("Plant 1").status(OrganizationStatus.INACTIVE).build();
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getActivePlant(plantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
    }

    /**
     * Both halves of the same guard: the warehouse itself, and its parent plant. The second is the one
     * that would silently disappear if someone "simplified" the condition, and it is the reason a
     * warehouse under a retired plant cannot be used even though its own status still says ACTIVE.
     */
    @Test
    @DisplayName("getActiveWarehouse: inactive warehouse OR inactive parent plant is RESOURCE_INACTIVE")
    void getActiveWarehouse_inactiveWarehouseOrParentPlant_isResourceInactive() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse inactiveWarehouse = warehouse(warehouseId, UUID.randomUUID());
        inactiveWarehouse.setStatus(OrganizationStatus.INACTIVE);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(inactiveWarehouse));

        assertThatThrownBy(() -> service.getActiveWarehouse(warehouseId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        UUID otherId = UUID.randomUUID();
        Warehouse underRetiredPlant = warehouse(otherId, UUID.randomUUID());
        underRetiredPlant.getPlant().setStatus(OrganizationStatus.INACTIVE);
        when(warehouseRepository.findById(otherId)).thenReturn(Optional.of(underRetiredPlant));

        assertThatThrownBy(() -> service.getActiveWarehouse(otherId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
    }

    /**
     * The boundary EH-2 was drawn around: "the record is retired" and "you picked a record from
     * somewhere else" are different problems with different remedies, and this one method can produce
     * either. Both are 422, so only the {@code code} separates them.
     */
    @Test
    @DisplayName("getActiveWarehouseInPlant: a warehouse from another plant is RESOURCE_SCOPE_MISMATCH")
    void getActiveWarehouseInPlant_warehouseOfAnotherPlant_isScopeMismatch() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse active = warehouse(warehouseId, UUID.randomUUID());
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.getActiveWarehouseInPlant(warehouseId, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));
    }

    private Company company(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, UUID companyId) {
        Plant plant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(company(companyId))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant)
                .code("WH1")
                .name("Warehouse 1")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
