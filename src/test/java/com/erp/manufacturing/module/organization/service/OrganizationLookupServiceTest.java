package com.erp.manufacturing.module.organization.service;

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
