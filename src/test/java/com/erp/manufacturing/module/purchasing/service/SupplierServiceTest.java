package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.CompanySupplierCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.ItemSupplierRequest;
import com.erp.manufacturing.module.purchasing.dto.SupplierCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.SupplierResponse;
import com.erp.manufacturing.module.purchasing.dto.SupplierUpdateRequest;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.ItemSupplierRepository;
import com.erp.manufacturing.module.purchasing.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierService tests")
class SupplierServiceTest {

    @Mock SupplierRepository supplierRepository;
    @Mock ItemSupplierRepository itemSupplierRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemLookupService itemLookupService;

    SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(
                supplierRepository,
                itemSupplierRepository,
                organizationLookupService,
                itemLookupService,
                new PurchasingMapper());
    }

    @Test
    void createSupplier_success() {
        Company company = company(UUID.randomUUID());
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(supplierRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "SUP-001")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier supplier = invocation.getArgument(0);
            supplier.setSupplierId(UUID.randomUUID());
            return supplier;
        });

        SupplierResponse response = service.create(new SupplierCreateRequest(
                company.getCompanyId(), " sup-001 ", "Main Supplier", "a@example.com", null, null, null, null, null));

        assertThat(response.code()).isEqualTo("SUP-001");
        assertThat(response.status()).isEqualTo(SupplierStatus.ACTIVE.name());
    }

    @Test
    void createSupplier_duplicateCodeFails() {
        Company company = company(UUID.randomUUID());
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(supplierRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "SUP-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SupplierCreateRequest(
                company.getCompanyId(), "SUP-001", "Supplier", null, null, null, null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));
    }

    /** G2-2: the company-path create takes the company from the path and stores the new contact fields. */
    @Test
    void createForCompany_usesThePathCompanyAndStoresContactAndNotes() {
        Company company = company(UUID.randomUUID());
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(supplierRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "PARTNER-01")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierResponse response = service.createForCompany(company.getCompanyId(), new CompanySupplierCreateRequest(
                " partner-01 ", "Demo Supplier", null, "0000123456", null, null, "  Demo Contact ", "   "));

        assertThat(response.companyId()).isEqualTo(company.getCompanyId());
        assertThat(response.code()).isEqualTo("PARTNER-01");
        assertThat(response.contactName()).isEqualTo("Demo Contact");
        assertThat(response.phone()).isEqualTo("0000123456");
        // Blank optional text is stored as null, not as whitespace.
        assertThat(response.notes()).isNull();
        verify(organizationLookupService).getActiveCompany(company.getCompanyId());
    }

    @Test
    void createForCompany_duplicateCodeFailsBeforeSaving() {
        Company company = company(UUID.randomUUID());
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(supplierRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "PARTNER-01")).thenReturn(true);

        assertThatThrownBy(() -> service.createForCompany(company.getCompanyId(), new CompanySupplierCreateRequest(
                "PARTNER-01", "Demo Supplier", null, null, null, null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));
        verify(supplierRepository, never()).save(any());
    }

    /** null keeps a field, a blank string clears an optional one (NEXT_PHASE_PLAN.md 9.2 D3). */
    @Test
    void update_nullKeepsContactAndNotes_blankClearsThem() {
        Supplier supplier = supplier(UUID.randomUUID(), company(UUID.randomUUID()), SupplierStatus.ACTIVE);
        supplier.setContactName("Old Contact");
        supplier.setNotes("Old notes");
        when(supplierRepository.findWithCompanyBySupplierId(supplier.getSupplierId())).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierResponse kept = service.update(supplier.getSupplierId(),
                new SupplierUpdateRequest(null, null, null, null, null, null, null));
        assertThat(kept.contactName()).isEqualTo("Old Contact");
        assertThat(kept.notes()).isEqualTo("Old notes");

        SupplierResponse cleared = service.update(supplier.getSupplierId(),
                new SupplierUpdateRequest(null, null, null, null, null, "", " "));
        assertThat(cleared.contactName()).isNull();
        assertThat(cleared.notes()).isNull();
    }

    @Test
    void activate_inactiveSupplier_becomesActive() {
        Supplier supplier = supplier(UUID.randomUUID(), company(UUID.randomUUID()), SupplierStatus.INACTIVE);
        when(supplierRepository.findWithCompanyBySupplierId(supplier.getSupplierId())).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierResponse response = service.activate(supplier.getSupplierId());

        assertThat(response.status()).isEqualTo(SupplierStatus.ACTIVE.name());
        verify(supplierRepository).save(supplier);
    }

    @Test
    void activate_alreadyActiveSupplier_isIdempotent() {
        Supplier supplier = supplier(UUID.randomUUID(), company(UUID.randomUUID()), SupplierStatus.ACTIVE);
        when(supplierRepository.findWithCompanyBySupplierId(supplier.getSupplierId())).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.activate(supplier.getSupplierId()).status()).isEqualTo(SupplierStatus.ACTIVE.name());
    }

    /** B108: a child cannot be reactivated under an inactive parent. */
    @Test
    void activate_whileTheCompanyIsInactive_throwsResourceInactiveAndStaysInactive() {
        Company company = company(UUID.randomUUID());
        company.setStatus(OrganizationStatus.INACTIVE);
        Supplier supplier = supplier(UUID.randomUUID(), company, SupplierStatus.INACTIVE);
        when(supplierRepository.findWithCompanyBySupplierId(supplier.getSupplierId())).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> service.activate(supplier.getSupplierId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
        assertThat(supplier.getStatus()).isEqualTo(SupplierStatus.INACTIVE);
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void addItemSupplier_secondPreferredActiveFails() {
        Company company = company(UUID.randomUUID());
        Item item = item(UUID.randomUUID(), company);
        Supplier supplier = supplier(UUID.randomUUID(), company, SupplierStatus.ACTIVE);
        when(itemLookupService.getActiveItem(item.getItemId())).thenReturn(item);
        when(supplierRepository.findWithCompanyBySupplierId(supplier.getSupplierId())).thenReturn(Optional.of(supplier));
        when(itemSupplierRepository.existsByItemItemIdAndSupplierSupplierId(item.getItemId(), supplier.getSupplierId()))
                .thenReturn(false);
        when(itemSupplierRepository.existsByItemItemIdAndPreferredIsTrueAndStatus(
                item.getItemId(), ItemSupplierStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.addItemSupplier(item.getItemId(), new ItemSupplierRequest(
                supplier.getSupplierId(), null, 3, new BigDecimal("1"), BigDecimal.ZERO, "USD", true)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
    }

    @Test
    void findActiveSupplier_inactiveSupplierFails() {
        Supplier supplier = supplier(UUID.randomUUID(), company(UUID.randomUUID()), SupplierStatus.INACTIVE);
        when(supplierRepository.findWithCompanyBySupplierId(supplier.getSupplierId())).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> service.findActiveSupplier(supplier.getSupplierId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
    }

    private Item item(UUID itemId, Company company) {
        return Item.builder()
                .itemId(itemId)
                .company(company)
                .code("ITEM")
                .name("Item")
                .type(ItemType.RAW_MATERIAL)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private Supplier supplier(UUID supplierId, Company company, SupplierStatus status) {
        return Supplier.builder()
                .supplierId(supplierId)
                .company(company)
                .code("SUP")
                .name("Supplier")
                .status(status)
                .build();
    }

    private Company company(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
