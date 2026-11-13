package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.dto.ItemCreateRequest;
import com.erp.manufacturing.module.inventory.dto.ItemUpdateRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.domain.UomStatus;
import com.erp.manufacturing.module.uom.service.UomLookupService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemService tests")
class ItemServiceTest {

    @Mock ItemRepository itemRepository;
    @Mock CompanyRepository companyRepository;
    @Mock UomLookupService uomLookupService;

    ItemService service;

    @BeforeEach
    void setUp() {
        service = new ItemService(itemRepository, new OrganizationLookupService(companyRepository, mock(PlantRepository.class), mock(WarehouseRepository.class)), uomLookupService, new InventoryMapper());
    }

    @Test
    void createItem_success_normalizesCodeAndUnit() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));
        when(itemRepository.existsByCompanyCompanyIdAndCode(companyId, "RM-001")).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uomLookupService.findByCode(List.of("KG"))).thenReturn(Map.of("KG", uom("KG", UomStatus.ACTIVE)));

        service.createItem(companyId, new ItemCreateRequest(
                "rm-001", "Steel Coil", ItemType.RAW_MATERIAL, "kg", true, false));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("RM-001");
        assertThat(captor.getValue().getUnit()).isEqualTo("KG");
        assertThat(captor.getValue().getStatus()).isEqualTo(ItemStatus.ACTIVE);
    }

    @Test
    void createItem_serialTrackedAlone_isRejectedBecauseSerialIsDeferred() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));

        assertThatThrownBy(() -> service.createItem(companyId, new ItemCreateRequest(
                "SN-001", "Router", ItemType.FINISHED_GOOD, "EA", false, true)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
        verify(itemRepository, never()).save(any());
    }

    @Test
    void createItem_bothLotAndSerialTracked_throwsOperationNotAllowedBeforeSaving() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));

        assertThatThrownBy(() -> service.createItem(companyId, new ItemCreateRequest(
                "SN-002", "Router", ItemType.FINISHED_GOOD, "EA", true, true)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(itemRepository, never()).save(any());
    }

    @Test
    void createItem_duplicateCodeWithinCompany_fails() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));
        when(itemRepository.existsByCompanyCompanyIdAndCode(companyId, "RM-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createItem(companyId, new ItemCreateRequest(
                "RM-001", "Steel Coil", ItemType.RAW_MATERIAL, "KG", true, false)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(itemRepository, never()).save(any());
    }

    @Test
    void createItem_underInactiveCompany_fails() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.INACTIVE)
                .build()));

        assertThatThrownBy(() -> service.createItem(companyId, new ItemCreateRequest(
                "RM-001", "Steel Coil", ItemType.RAW_MATERIAL, "KG", true, false)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
    }

    @Test
    void activateItem_underActiveCompany_succeeds() {
        UUID itemId = UUID.randomUUID();
        Item item = Item.builder()
                .itemId(itemId)
                .company(activeCompany(UUID.randomUUID()))
                .code("RM-001")
                .name("Steel Coil")
                .type(ItemType.RAW_MATERIAL)
                .unit("KG")
                .lotTracked(true)
                .status(ItemStatus.INACTIVE)
                .build();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);

        var response = service.activateItem(itemId);

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(itemRepository).save(item);
    }

    @Test
    void activateItem_alreadyActive_isIdempotent() {
        UUID itemId = UUID.randomUUID();
        Item item = Item.builder()
                .itemId(itemId)
                .company(activeCompany(UUID.randomUUID()))
                .code("RM-001")
                .name("Steel Coil")
                .type(ItemType.RAW_MATERIAL)
                .unit("KG")
                .lotTracked(true)
                .status(ItemStatus.ACTIVE)
                .build();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);

        var response = service.activateItem(itemId);

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(itemRepository).save(item);
    }

    @Test
    void activateItem_underInactiveCompany_failsBeforeSaving() {
        UUID itemId = UUID.randomUUID();
        Company inactiveCompany = Company.builder()
                .companyId(UUID.randomUUID())
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.INACTIVE)
                .build();
        Item item = Item.builder()
                .itemId(itemId)
                .company(inactiveCompany)
                .code("RM-001")
                .name("Steel Coil")
                .type(ItemType.RAW_MATERIAL)
                .unit("KG")
                .lotTracked(true)
                .status(ItemStatus.INACTIVE)
                .build();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.activateItem(itemId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        verify(itemRepository, never()).save(any());
    }

    // ── G2-4 (D5): unit ↔ UOM master, list filters ─────────────────────────

    @Test
    void createItem_unitUnknownToTheUomCatalogue_throwsNotFoundBeforeSaving() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));
        when(itemRepository.existsByCompanyCompanyIdAndCode(companyId, "RM-001")).thenReturn(false);
        when(uomLookupService.findByCode(List.of("BOX"))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.createItem(companyId, new ItemCreateRequest(
                "RM-001", "Steel Coil", ItemType.RAW_MATERIAL, " box ", false, false)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
        verify(itemRepository, never()).save(any());
    }

    @Test
    void createItem_inactiveUom_throwsResourceInactiveBeforeSaving() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));
        when(itemRepository.existsByCompanyCompanyIdAndCode(companyId, "RM-001")).thenReturn(false);
        when(uomLookupService.findByCode(List.of("KG"))).thenReturn(Map.of("KG", uom("KG", UomStatus.INACTIVE)));

        assertThatThrownBy(() -> service.createItem(companyId, new ItemCreateRequest(
                "RM-001", "Steel Coil", ItemType.RAW_MATERIAL, "KG", false, false)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
        verify(itemRepository, never()).save(any());
    }

    @Test
    void updateItem_changingTheUnit_throwsOperationNotAllowedAndSavesNothing() {
        Item item = activeItemWithUnit("KG");
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.updateItem(item.getItemId(), new ItemUpdateRequest("Renamed", "PCS")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
        assertThat(item.getName()).isEqualTo("Steel Coil");
        verify(itemRepository, never()).save(any());
    }

    /**
     * The FE adapter resends the unit unchanged; that must keep working, and must not consult the UOM
     * catalogue — legacy items may carry a unit it never had.
     */
    @Test
    void updateItem_sameUnitInAnotherCase_isAcceptedWithoutAUomLookup() {
        Item item = activeItemWithUnit("LEGACY");
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateItem(item.getItemId(), new ItemUpdateRequest(" Renamed ", " legacy "));

        assertThat(item.getName()).isEqualTo("Renamed");
        assertThat(item.getUnit()).isEqualTo("LEGACY");
        verifyNoInteractions(uomLookupService);
    }

    @Test
    void listItems_forwardsEveryFilterAndTheTrimmedSearchInOneQuery() {
        UUID companyId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        when(companyRepository.existsById(companyId)).thenReturn(true);
        when(itemRepository.search(companyId, "coil", ItemStatus.ACTIVE, ItemType.RAW_MATERIAL, true, pageable))
                .thenReturn(Page.empty());

        service.listItems(companyId, "  coil ", ItemStatus.ACTIVE, ItemType.RAW_MATERIAL, true, pageable);

        verify(itemRepository).search(companyId, "coil", ItemStatus.ACTIVE, ItemType.RAW_MATERIAL, true, pageable);
    }

    @Test
    void listItems_blankSearchIsForwardedAsNull() {
        UUID companyId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        when(companyRepository.existsById(companyId)).thenReturn(true);
        when(itemRepository.search(companyId, null, null, null, null, pageable)).thenReturn(Page.empty());

        service.listItems(companyId, "   ", null, null, null, pageable);

        verify(itemRepository).search(companyId, null, null, null, null, pageable);
    }

    private Item activeItemWithUnit(String unit) {
        return Item.builder()
                .itemId(UUID.randomUUID())
                .company(activeCompany(UUID.randomUUID()))
                .code("RM-001")
                .name("Steel Coil")
                .type(ItemType.RAW_MATERIAL)
                .unit(unit)
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private Uom uom(String code, UomStatus status) {
        return Uom.builder().code(code).name(code).status(status).build();
    }

    private Company activeCompany(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
