package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.dto.*;
import com.erp.manufacturing.module.organization.mapper.OrganizationMapper;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService tests")
class OrganizationServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock PlantRepository plantRepository;
    @Mock WarehouseRepository warehouseRepository;

    OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(
                companyRepository,
                plantRepository,
                warehouseRepository,
                new OrganizationMapper());
    }

    @Test
    void createCompany_success_normalizesCode() {
        when(companyRepository.existsByCode("ACME")).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createCompany(new CompanyCreateRequest("acme", "ACME Manufacturing"));

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("ACME");
        assertThat(captor.getValue().getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void createCompany_duplicateCode_fails() {
        when(companyRepository.existsByCode("ACME")).thenReturn(true);

        assertThatThrownBy(() -> service.createCompany(new CompanyCreateRequest("ACME", "ACME")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(companyRepository, never()).save(any());
    }

    @Test
    void createPlant_underInactiveCompany_fails() {
        UUID companyId = UUID.randomUUID();
        Company inactive = Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.INACTIVE)
                .build();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.createPlant(
                companyId, new PlantCreateRequest("P1", "Plant 1", null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
    }

    @Test
    void createPlant_duplicateCodeWithinCompany_fails() {
        UUID companyId = UUID.randomUUID();
        Company company = activeCompany(companyId);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(plantRepository.existsByCompanyCompanyIdAndCode(companyId, "P1")).thenReturn(true);

        assertThatThrownBy(() -> service.createPlant(
                companyId, new PlantCreateRequest("P1", "Plant 1", null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));
    }

    @Test
    void createWarehouse_underInactivePlant_fails() {
        UUID plantId = UUID.randomUUID();
        Plant inactive = Plant.builder()
                .plantId(plantId)
                .company(activeCompany(UUID.randomUUID()))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.INACTIVE)
                .build();
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.createWarehouse(
                plantId, new WarehouseCreateRequest("RM", "Raw", WarehouseType.RAW_MATERIAL)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
    }

    @Test
    void createWarehouse_duplicateCodeWithinPlant_fails() {
        UUID plantId = UUID.randomUUID();
        Plant plant = activePlant(plantId);
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));
        when(warehouseRepository.existsByPlantPlantIdAndCode(plantId, "RM")).thenReturn(true);

        assertThatThrownBy(() -> service.createWarehouse(
                plantId, new WarehouseCreateRequest("RM", "Raw", WarehouseType.RAW_MATERIAL)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));
    }

    @Test
    void deactivateWarehouse_softDeletesOnly() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(activePlant(UUID.randomUUID()))
                .code("RM")
                .name("Raw")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(warehouse)).thenReturn(warehouse);

        WarehouseResponse response = service.deactivateWarehouse(warehouseId);

        assertThat(response.status()).isEqualTo("INACTIVE");
        verify(warehouseRepository).save(warehouse);
        verify(warehouseRepository, never()).delete(any());
    }

    @Test
    void activatePlant_underActiveCompany_succeeds() {
        UUID plantId = UUID.randomUUID();
        Plant plant = Plant.builder()
                .plantId(plantId)
                .company(activeCompany(UUID.randomUUID()))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.INACTIVE)
                .build();
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));
        when(plantRepository.save(plant)).thenReturn(plant);

        PlantResponse response = service.activatePlant(plantId);

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(plantRepository).save(plant);
    }

    @Test
    void activatePlant_alreadyActive_isIdempotent() {
        UUID plantId = UUID.randomUUID();
        Plant plant = Plant.builder()
                .plantId(plantId)
                .company(activeCompany(UUID.randomUUID()))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));
        when(plantRepository.save(plant)).thenReturn(plant);

        PlantResponse response = service.activatePlant(plantId);

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(plantRepository).save(plant);
    }

    @Test
    void activatePlant_underInactiveCompany_failsBeforeSaving() {
        UUID plantId = UUID.randomUUID();
        Company inactiveCompany = Company.builder()
                .companyId(UUID.randomUUID())
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.INACTIVE)
                .build();
        Plant plant = Plant.builder()
                .plantId(plantId)
                .company(inactiveCompany)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.INACTIVE)
                .build();
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));

        assertThatThrownBy(() -> service.activatePlant(plantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        verify(plantRepository, never()).save(any());
    }

    @Test
    void activateWarehouse_underActivePlant_succeeds() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(activePlant(UUID.randomUUID()))
                .code("RM")
                .name("Raw")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.INACTIVE)
                .build();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(warehouse)).thenReturn(warehouse);

        WarehouseResponse response = service.activateWarehouse(warehouseId);

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(warehouseRepository).save(warehouse);
    }

    @Test
    void activateWarehouse_alreadyActive_isIdempotent() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(activePlant(UUID.randomUUID()))
                .code("RM")
                .name("Raw")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(warehouse)).thenReturn(warehouse);

        WarehouseResponse response = service.activateWarehouse(warehouseId);

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(warehouseRepository).save(warehouse);
    }

    @Test
    void activateWarehouse_underInactivePlant_failsBeforeSaving() {
        UUID warehouseId = UUID.randomUUID();
        Plant inactivePlant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(activeCompany(UUID.randomUUID()))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.INACTIVE)
                .build();
        Warehouse warehouse = Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(inactivePlant)
                .code("RM")
                .name("Raw")
                .type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.INACTIVE)
                .build();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.activateWarehouse(warehouseId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        verify(warehouseRepository, never()).save(any());
    }

    // ── G2-4: list filters, timezone, deactivate guard ─────────────────────

    @Test
    void listPlants_forwardsTrimmedSearchAndStatusToOneQuery() {
        UUID companyId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        when(companyRepository.existsById(companyId)).thenReturn(true);
        when(plantRepository.search(companyId, OrganizationStatus.ACTIVE, "hanoi", pageable)).thenReturn(Page.empty());

        service.listPlants(companyId, "  hanoi ", OrganizationStatus.ACTIVE, pageable);

        verify(plantRepository).search(companyId, OrganizationStatus.ACTIVE, "hanoi", pageable);
    }

    @Test
    void listWarehouses_blankSearchIsForwardedAsNull() {
        UUID plantId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        when(plantRepository.existsById(plantId)).thenReturn(true);
        when(warehouseRepository.search(plantId, null, null, pageable)).thenReturn(Page.empty());

        service.listWarehouses(plantId, "   ", null, pageable);

        verify(warehouseRepository).search(plantId, null, null, pageable);
    }

    /** It used to be stored as typed and fail later as a 500 when a work order was released. */
    @Test
    void createPlant_unknownTimezone_isAFieldErrorOnTimezoneAndNothingIsSaved() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));
        when(plantRepository.existsByCompanyCompanyIdAndCode(companyId, "PLANT-01")).thenReturn(false);

        assertThatThrownBy(() -> service.createPlant(companyId,
                new PlantCreateRequest("PLANT-01", "Hanoi Plant", "Mars/Olympus")))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("timezone");
                });
        verify(plantRepository, never()).save(any());
    }

    @Test
    void createPlant_blankTimezone_defaultsToHoChiMinh() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));
        when(plantRepository.existsByCompanyCompanyIdAndCode(companyId, "PLANT-01")).thenReturn(false);
        when(plantRepository.save(any(Plant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPlant(companyId, new PlantCreateRequest("PLANT-01", "Hanoi Plant", "  "));

        ArgumentCaptor<Plant> captor = ArgumentCaptor.forClass(Plant.class);
        verify(plantRepository).save(captor.capture());
        assertThat(captor.getValue().getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void updatePlant_unknownTimezone_isAFieldErrorAndTheStoredZoneIsKept() {
        UUID plantId = UUID.randomUUID();
        Plant plant = activePlant(plantId);
        plant.setTimezone("Asia/Ho_Chi_Minh");
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));

        assertThatThrownBy(() -> service.updatePlant(plantId, new PlantUpdateRequest("Renamed", "GMT+99")))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("timezone"));
        assertThat(plant.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        verify(plantRepository, never()).save(any());
    }

    @Test
    void updatePlant_validTimezone_isStoredTrimmed() {
        UUID plantId = UUID.randomUUID();
        Plant plant = activePlant(plantId);
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));
        when(plantRepository.save(any(Plant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updatePlant(plantId, new PlantUpdateRequest("Renamed", " America/New_York "));

        assertThat(plant.getTimezone()).isEqualTo("America/New_York");
    }

    /** D4: refused, never cascaded — the plant stays ACTIVE and nothing is written. */
    @Test
    void deactivatePlant_withAnActiveWarehouse_throwsActiveChildrenExistAndStaysActive() {
        UUID plantId = UUID.randomUUID();
        Plant plant = activePlant(plantId);
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));
        when(warehouseRepository.existsByPlantPlantIdAndStatus(plantId, OrganizationStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivatePlant(plantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.ACTIVE_CHILDREN_EXIST));
        assertThat(plant.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        verify(plantRepository, never()).save(any());
    }

    @Test
    void deactivatePlant_withOnlyInactiveWarehouses_succeeds() {
        UUID plantId = UUID.randomUUID();
        Plant plant = activePlant(plantId);
        when(plantRepository.findById(plantId)).thenReturn(Optional.of(plant));
        when(warehouseRepository.existsByPlantPlantIdAndStatus(plantId, OrganizationStatus.ACTIVE)).thenReturn(false);
        when(plantRepository.save(any(Plant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deactivatePlant(plantId);

        assertThat(plant.getStatus()).isEqualTo(OrganizationStatus.INACTIVE);
        verify(warehouseRepository).existsByPlantPlantIdAndStatus(plantId, OrganizationStatus.ACTIVE);
    }

    private Company activeCompany(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Plant activePlant(UUID plantId) {
        return Plant.builder()
                .plantId(plantId)
                .company(activeCompany(UUID.randomUUID()))
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
