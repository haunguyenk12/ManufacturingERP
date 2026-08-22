package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.dto.ItemCreateRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Locks the dedicated Item Master permission codes. Item permissions intentionally no longer reuse
 * PERM_INVENTORY_* because stock access and company-owned item master access have different scope
 * semantics.
 */
@SpringJUnitConfig(ItemMethodSecurityTest.Config.class)
@DisplayName("ItemService method security — dedicated Item permissions")
class ItemMethodSecurityTest {

    @Autowired ItemService itemService;
    @Autowired InventoryPermissionGuard inventoryPermissionGuard;
    @Autowired ItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        reset(inventoryPermissionGuard, itemRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activateItem_deniedWhenItemManageAccessMissing() {
        UUID itemId = UUID.randomUUID();
        when(inventoryPermissionGuard.hasItemAccess(any(), eq("PERM_ITEM_MANAGE"), eq(itemId)))
                .thenReturn(false);

        assertThatThrownBy(() -> itemService.activateItem(itemId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(itemRepository);
        verify(inventoryPermissionGuard).hasItemAccess(any(), eq("PERM_ITEM_MANAGE"), eq(itemId));
    }

    @Test
    void activateItem_allowedWhenItemManageAccessPresent() {
        UUID itemId = UUID.randomUUID();
        Item item = Item.builder()
                .itemId(itemId)
                .company(Company.builder()
                        .companyId(UUID.randomUUID())
                        .code("ACME")
                        .name("ACME")
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .code("RM-001")
                .name("Steel Coil")
                .type(ItemType.RAW_MATERIAL)
                .unit("KG")
                .lotTracked(true)
                .status(ItemStatus.INACTIVE)
                .build();
        when(inventoryPermissionGuard.hasItemAccess(any(), eq("PERM_ITEM_MANAGE"), eq(itemId)))
                .thenReturn(true);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);

        assertThat(itemService.activateItem(itemId).status()).isEqualTo("ACTIVE");
    }

    @Test
    void listItems_usesItemReadAtCompanyOrPlantScope() {
        UUID companyId = UUID.randomUUID();
        when(inventoryPermissionGuard.hasItemCompanyAccess(any(), eq("PERM_ITEM_READ"), eq(companyId)))
                .thenReturn(false);

        assertThatThrownBy(() -> itemService.listItems(companyId, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(inventoryPermissionGuard)
                .hasItemCompanyAccess(any(), eq("PERM_ITEM_READ"), eq(companyId));
        verifyNoInteractions(itemRepository);
    }

    @Test
    void createItem_usesItemManageAtCompanyOrPlantScope() {
        UUID companyId = UUID.randomUUID();
        when(inventoryPermissionGuard.hasItemCompanyAccess(any(), eq("PERM_ITEM_MANAGE"), eq(companyId)))
                .thenReturn(false);

        assertThatThrownBy(() -> itemService.createItem(companyId, new ItemCreateRequest(
                "RM-001", "Steel Coil", ItemType.RAW_MATERIAL, "KG", true, false)))
                .isInstanceOf(AccessDeniedException.class);

        verify(inventoryPermissionGuard)
                .hasItemCompanyAccess(any(), eq("PERM_ITEM_MANAGE"), eq(companyId));
        verifyNoInteractions(itemRepository);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        ItemService itemService(ItemRepository itemRepository,
                                 CompanyRepository companyRepository,
                                 InventoryMapper mapper) {
            return new ItemService(itemRepository, companyRepository, mapper);
        }

        @Bean
        InventoryMapper inventoryMapper() {
            return new InventoryMapper();
        }

        @Bean(name = "inventoryPermissionGuard")
        InventoryPermissionGuard inventoryPermissionGuard() {
            return mock(InventoryPermissionGuard.class);
        }

        @Bean
        ItemRepository itemRepository() {
            return mock(ItemRepository.class);
        }

        @Bean
        CompanyRepository companyRepository() {
            return mock(CompanyRepository.class);
        }
    }
}
