package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSetting;
import com.erp.manufacturing.module.inventory.domain.ItemWarehouseSettingStatus;
import com.erp.manufacturing.module.inventory.dto.ItemWarehouseSettingRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.ItemWarehouseSettingRepository;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ItemWarehouseSettingMethodSecurityTest.Config.class)
@DisplayName("ItemWarehouseSettingService method security")
class ItemWarehouseSettingMethodSecurityTest {

    @Autowired ItemWarehouseSettingService settingService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired InventorySettingPermissionGuard inventorySettingPermissionGuard;
    @Autowired ItemLookupService itemLookupService;
    @Autowired ItemWarehouseSettingRepository settingRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, inventorySettingPermissionGuard, itemLookupService, settingRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void upsert_deniedWhenWarehouseManageScopeMissing() {
        UUID warehouseId = UUID.randomUUID();
        ItemWarehouseSettingRequest request = new ItemWarehouseSettingRequest(
                UUID.randomUUID(), warehouseId, BigDecimal.ONE, BigDecimal.ZERO, 1);
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_MANAGE"), eq("WAREHOUSE"), eq(warehouseId)))
                .thenReturn(false);

        assertThatThrownBy(() -> settingService.upsert(request, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(itemLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_MANAGE"), eq("WAREHOUSE"), eq(warehouseId));
    }

    @Test
    void deactivate_deniedWhenSettingGuardDenies() {
        UUID settingId = UUID.randomUUID();
        when(inventorySettingPermissionGuard.hasSettingAccess(any(), eq("PERM_INVENTORY_MANAGE"), eq(settingId)))
                .thenReturn(false);

        assertThatThrownBy(() -> settingService.deactivate(settingId, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(settingRepository);
        verify(inventorySettingPermissionGuard).hasSettingAccess(any(), eq("PERM_INVENTORY_MANAGE"), eq(settingId));
    }

    @Test
    void deactivate_allowedWhenSettingGuardAllows() {
        UUID settingId = UUID.randomUUID();
        when(inventorySettingPermissionGuard.hasSettingAccess(any(), eq("PERM_INVENTORY_MANAGE"), eq(settingId)))
                .thenReturn(true);
        // A setting always has a warehouse in a plant (NOT NULL FK); G2-3 reads that plant to check X-Plant-Id.
        Plant plant = Plant.builder().plantId(UUID.randomUUID()).build();
        Warehouse warehouse = Warehouse.builder().warehouseId(UUID.randomUUID()).plant(plant).build();
        when(settingRepository.findWithDetailsBySettingId(settingId)).thenReturn(Optional.of(
                ItemWarehouseSetting.builder()
                        .settingId(settingId)
                        .warehouse(warehouse)
                        .status(ItemWarehouseSettingStatus.ACTIVE)
                        .build()));

        assertThatCode(() -> settingService.deactivate(settingId, null)).doesNotThrowAnyException();
    }

    /** G2-3: listing by plant is a PLANT-scoped check on the exact permission string. */
    @Test
    void list_byPlant_deniedWhenInventoryReadMissingOnThePlant() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("PLANT"), eq(plantId)))
                .thenReturn(false);

        assertThatThrownBy(() -> settingService.list(plantId, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(settingRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("PLANT"), eq(plantId));
        verify(permissionGuard, never()).hasPermission(any(), any());
    }

    @Test
    void list_byPlant_allowedWhenInventoryReadPresentOnThePlant() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("PLANT"), eq(plantId)))
                .thenReturn(true);
        when(settingRepository.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        assertThatCode(() -> settingService.list(plantId, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("PLANT"), eq(plantId));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        ItemWarehouseSettingService itemWarehouseSettingService(ItemWarehouseSettingRepository settingRepository,
                                                               ItemLookupService itemLookupService,
                                                               WarehouseRepository warehouseRepository,
                                                               InventoryMapper mapper) {
            return new ItemWarehouseSettingService(
                    settingRepository,
                    itemLookupService,
                    mock(ItemRepository.class),
                    new OrganizationLookupService(mock(CompanyRepository.class), mock(PlantRepository.class), warehouseRepository),
                    new PlantContextResolver(),
                    mapper);
        }

        @Bean
        InventoryMapper inventoryMapper() {
            return new InventoryMapper();
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean(name = "inventorySettingPermissionGuard")
        InventorySettingPermissionGuard inventorySettingPermissionGuard() {
            return mock(InventorySettingPermissionGuard.class);
        }

        @Bean
        ItemWarehouseSettingRepository itemWarehouseSettingRepository() {
            return mock(ItemWarehouseSettingRepository.class);
        }

        @Bean
        ItemLookupService itemLookupService() {
            return mock(ItemLookupService.class);
        }

        @Bean
        WarehouseRepository warehouseRepository() {
            return mock(WarehouseRepository.class);
        }
    }
}
