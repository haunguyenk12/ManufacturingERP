package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
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
 * Rule R2: covers only the new {@code activateItem} method (FE contract fix, 2026-08-06) — the repo
 * had no method-security test at all for {@link ItemService} before this. Retrofitting the existing
 * create/update/deactivate methods is out of scope for this change.
 */
@SpringJUnitConfig(ItemMethodSecurityTest.Config.class)
@DisplayName("ItemService method security — activateItem")
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
    void activateItem_deniedWhenInventoryManageAccessMissing() {
        UUID itemId = UUID.randomUUID();
        when(inventoryPermissionGuard.hasItemAccess(any(), eq("PERM_INVENTORY_MANAGE"), eq(itemId)))
                .thenReturn(false);

        assertThatThrownBy(() -> itemService.activateItem(itemId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(itemRepository);
        verify(inventoryPermissionGuard).hasItemAccess(any(), eq("PERM_INVENTORY_MANAGE"), eq(itemId));
    }

    @Test
    void activateItem_allowedWhenInventoryManageAccessPresent() {
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
        when(inventoryPermissionGuard.hasItemAccess(any(), eq("PERM_INVENTORY_MANAGE"), eq(itemId)))
                .thenReturn(true);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);

        assertThat(itemService.activateItem(itemId).status()).isEqualTo("ACTIVE");
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
