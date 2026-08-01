package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.StockReceiveRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(InventoryMethodSecurityTest.Config.class)
@DisplayName("InventoryService method security")
class InventoryMethodSecurityTest {

    @Autowired InventoryService inventoryService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired InventoryMovementService movementService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, movementService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void receive_deniedWhenWarehouseScopeMissing() {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        StockReceiveRequest request = new StockReceiveRequest(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null);
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(warehouseId)))
                .thenReturn(false);

        assertThatThrownBy(() -> inventoryService.receive(request, "KEY-1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(movementService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(warehouseId));
    }

    @Test
    void receive_allowedWhenWarehouseScopePresent() {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        StockReceiveRequest request = new StockReceiveRequest(
                itemId, warehouseId, null, null, BigDecimal.ONE, null, null, null);
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(warehouseId)))
                .thenReturn(true);
        when(movementService.receive(any(InventoryReceiveCommand.class), eq("KEY-1")))
                .thenReturn(new InventoryMovementResult(movement(itemId, warehouseId), true));

        assertThatCode(() -> inventoryService.receive(request, "KEY-1")).doesNotThrowAnyException();
    }

    private StockMovement movement(UUID itemId, UUID warehouseId) {
        Company company = Company.builder()
                .companyId(UUID.randomUUID())
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Plant plant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        return StockMovement.builder()
                .movementId(UUID.randomUUID())
                .item(Item.builder()
                        .itemId(itemId)
                        .company(company)
                        .code("RM-001")
                        .name("Raw material")
                        .type(ItemType.RAW_MATERIAL)
                        .unit("EA")
                        .status(ItemStatus.ACTIVE)
                        .build())
                .warehouse(Warehouse.builder()
                        .warehouseId(warehouseId)
                        .plant(plant)
                        .code("WH1")
                        .name("Warehouse 1")
                        .type(WarehouseType.RAW_MATERIAL)
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(BigDecimal.ONE)
                .idempotencyKey("KEY-1")
                .createdAt(Instant.now())
                .build();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        InventoryService inventoryService(InventoryMovementService movementService,
                                          WarehouseRepository warehouseRepository,
                                          StockBalanceRepository balanceRepository,
                                          StockMovementRepository movementRepository,
                                          InventoryMapper mapper) {
            return new InventoryService(
                    movementService,
                    warehouseRepository,
                    balanceRepository,
                    movementRepository,
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

        @Bean
        WarehouseRepository warehouseRepository() {
            return mock(WarehouseRepository.class);
        }

        @Bean
        InventoryMovementService inventoryMovementService() {
            return mock(InventoryMovementService.class);
        }

        @Bean
        StockBalanceRepository stockBalanceRepository() {
            return mock(StockBalanceRepository.class);
        }

        @Bean
        StockMovementRepository stockMovementRepository() {
            return mock(StockMovementRepository.class);
        }
    }
}
