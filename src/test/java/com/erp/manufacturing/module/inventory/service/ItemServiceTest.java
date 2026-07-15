package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.dto.ItemCreateRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    ItemService service;

    @BeforeEach
    void setUp() {
        service = new ItemService(itemRepository, companyRepository, new InventoryMapper());
    }

    @Test
    void createItem_success_normalizesCodeAndUnit() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));
        when(itemRepository.existsByCompanyCompanyIdAndCode(companyId, "RM-001")).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createItem(companyId, new ItemCreateRequest(
                "rm-001", "Steel Coil", ItemType.RAW_MATERIAL, "kg", true));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("RM-001");
        assertThat(captor.getValue().getUnit()).isEqualTo("KG");
        assertThat(captor.getValue().getStatus()).isEqualTo(ItemStatus.ACTIVE);
    }

    @Test
    void createItem_duplicateCodeWithinCompany_fails() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(activeCompany(companyId)));
        when(itemRepository.existsByCompanyCompanyIdAndCode(companyId, "RM-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createItem(companyId, new ItemCreateRequest(
                "RM-001", "Steel Coil", ItemType.RAW_MATERIAL, "KG", true)))
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
                "RM-001", "Steel Coil", ItemType.RAW_MATERIAL, "KG", true)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
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
