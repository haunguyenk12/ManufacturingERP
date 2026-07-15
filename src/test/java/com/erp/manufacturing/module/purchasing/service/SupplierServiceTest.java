package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.ItemSupplierRequest;
import com.erp.manufacturing.module.purchasing.dto.SupplierCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.SupplierResponse;
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
                company.getCompanyId(), " sup-001 ", "Main Supplier", "a@example.com", null, null, null));

        assertThat(response.code()).isEqualTo("SUP-001");
        assertThat(response.status()).isEqualTo(SupplierStatus.ACTIVE.name());
    }

    @Test
    void createSupplier_duplicateCodeFails() {
        Company company = company(UUID.randomUUID());
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(supplierRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "SUP-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SupplierCreateRequest(
                company.getCompanyId(), "SUP-001", "Supplier", null, null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));
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
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
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
