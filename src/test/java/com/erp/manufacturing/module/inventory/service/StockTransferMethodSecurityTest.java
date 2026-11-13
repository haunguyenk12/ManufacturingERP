package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.inventory.dto.StockTransferLineRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.*;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Rule R2: deny branches pin the exact permission and scope with {@code verify}; allow branches exist. */
@SpringJUnitConfig(StockTransferMethodSecurityTest.Config.class)
@DisplayName("StockTransferService method security (G4-2)")
class StockTransferMethodSecurityTest {

    @Autowired StockTransferService transferService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired InventoryPermissionGuard inventoryPermissionGuard;
    @Autowired StockTransferRepository transferRepository;
    @Autowired WarehouseRepository warehouseRepository;

    private static final UUID FROM = UUID.randomUUID();
    private static final UUID TO = UUID.randomUUID();
    private static final UUID PLANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, inventoryPermissionGuard, transferRepository, warehouseRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private StockTransferRequest request() {
        return new StockTransferRequest(FROM, TO, "Move",
                List.of(new StockTransferLineRequest(UUID.randomUUID(), null, BigDecimal.ONE)));
    }

    @Test
    void create_deniedWhenTheDestinationWarehouseIsOutOfScope() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(FROM)))
                .thenReturn(true);
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(TO)))
                .thenReturn(false);

        assertThatThrownBy(() -> transferService.create(request(), "KEY-1", null))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(FROM));
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(TO));
        verifyNoInteractions(transferRepository, warehouseRepository);
    }

    @Test
    void create_deniedWhenTheSourceWarehouseIsOutOfScope() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(FROM)))
                .thenReturn(false);

        assertThatThrownBy(() -> transferService.create(request(), "KEY-1", null))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(FROM));
        verifyNoInteractions(transferRepository, warehouseRepository);
    }

    @Test
    void create_allowedWhenBothWarehousesAreInScope() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), any()))
                .thenReturn(true);
        // Allowed through the guard; the service then fails on an unknown warehouse, which proves it ran.
        assertThatThrownBy(() -> transferService.create(request(), "KEY-1", null))
                .isNotInstanceOf(AccessDeniedException.class);
        verify(transferRepository).findByIdempotencyKey("KEY-1");
    }

    @Test
    void list_byPlant_deniedWhenPlantReadScopeMissing() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("PLANT"), eq(PLANT)))
                .thenReturn(false);

        assertThatThrownBy(() -> transferService.list(PLANT, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("PLANT"), eq(PLANT));
        verifyNoInteractions(transferRepository);
    }

    @Test
    void list_byWarehouse_isAuthorizedOnTheWarehouseNotThePlant() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("WAREHOUSE"), eq(FROM)))
                .thenReturn(false);

        assertThatThrownBy(() -> transferService.list(PLANT, FROM, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("WAREHOUSE"), eq(FROM));
        verify(permissionGuard, never()).hasResourceAccess(any(), any(), eq("PLANT"), any());
    }

    @Test
    void list_allowedWhenPlantReadScopePresent() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("PLANT"), eq(PLANT)))
                .thenReturn(true);
        when(transferRepository.search(any(), any(), any(), any())).thenReturn(Page.empty());

        assertThatCode(() -> transferService.list(PLANT, null, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void get_deniedWhenNeitherSideIsReadable() {
        UUID transferId = UUID.randomUUID();
        when(inventoryPermissionGuard.hasTransferAccess(any(), eq("PERM_INVENTORY_READ"), eq(transferId)))
                .thenReturn(false);

        assertThatThrownBy(() -> transferService.get(transferId)).isInstanceOf(AccessDeniedException.class);

        verify(inventoryPermissionGuard).hasTransferAccess(any(), eq("PERM_INVENTORY_READ"), eq(transferId));
        verifyNoInteractions(transferRepository);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        StockTransferService stockTransferService(StockTransferRepository transferRepository,
                                                  WarehouseRepository warehouseRepository) {
            return new StockTransferService(transferRepository, new OrganizationLookupService(mock(CompanyRepository.class), mock(PlantRepository.class), warehouseRepository),
                    mock(ItemRepository.class), mock(InventoryLotRepository.class),
                    mock(StockBalanceRepository.class), mock(StockMovementRepository.class),
                    new IdempotencySupport(new ObjectMapper()), new TraceIdProvider(),
                    new PlantContextResolver(), mock(UserLookupService.class), new InventoryMapper());
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean(name = "inventoryPermissionGuard")
        InventoryPermissionGuard inventoryPermissionGuard() {
            return mock(InventoryPermissionGuard.class);
        }

        @Bean
        StockTransferRepository stockTransferRepository() {
            return mock(StockTransferRepository.class);
        }

        @Bean
        WarehouseRepository warehouseRepository() {
            return mock(WarehouseRepository.class);
        }
    }
}
