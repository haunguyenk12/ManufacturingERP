package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.MovementDirection;
import com.erp.manufacturing.module.inventory.domain.MovementType;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.dto.InventoryLotStatusChangeRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.workorder.service.query.LotQcOriginLookupService;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Rule R2: every deny branch is paired with {@code verify(...)} pinning the exact permission
 * string/scope, plus at least one allow branch.
 */
@SpringJUnitConfig(InventoryLotMethodSecurityTest.Config.class)
@DisplayName("InventoryLotService method security")
class InventoryLotMethodSecurityTest {

    @Autowired InventoryLotService lotService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired InventoryPermissionGuard inventoryPermissionGuard;
    @Autowired StockBalanceRepository balanceRepository;
    @Autowired StockMovementRepository movementRepository;
    @Autowired InventoryLotRepository lotRepository;
    @Autowired InventoryMovementService movementService;

    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID LOT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, inventoryPermissionGuard, balanceRepository, movementRepository,
                lotRepository, movementService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_deniedWhenWarehouseScopeMissing() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("WAREHOUSE"), eq(WAREHOUSE_ID)))
                .thenReturn(false);

        assertThatThrownBy(() -> lotService.list(
                WAREHOUSE_ID, null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(balanceRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("WAREHOUSE"), eq(WAREHOUSE_ID));
    }

    @Test
    void list_allowedWhenWarehouseScopePresent() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("WAREHOUSE"), eq(WAREHOUSE_ID)))
                .thenReturn(true);
        when(balanceRepository.searchLots(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        assertThatCode(() -> lotService.list(
                WAREHOUSE_ID, null, null, null, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void get_deniedWhenLotAccessMissing() {
        when(inventoryPermissionGuard.hasLotAccess(any(), eq("PERM_INVENTORY_READ"), eq(LOT_ID)))
                .thenReturn(false);

        assertThatThrownBy(() -> lotService.get(LOT_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(inventoryPermissionGuard).hasLotAccess(any(), eq("PERM_INVENTORY_READ"), eq(LOT_ID));
    }

    @Test
    void get_allowedWhenLotAccessPresent() {
        when(inventoryPermissionGuard.hasLotAccess(any(), eq("PERM_INVENTORY_READ"), eq(LOT_ID)))
                .thenReturn(true);
        when(lotRepository.findById(LOT_ID)).thenReturn(Optional.of(lot()));
        when(balanceRepository.findByLotLotId(LOT_ID)).thenReturn(List.of());
        when(movementRepository.findFirstByLotLotIdAndMovementTypeOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Optional.empty());

        assertThatCode(() -> lotService.get(LOT_ID)).doesNotThrowAnyException();
    }

    @Test
    void changeStatus_deniedWhenWarehouseScopeMissing() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(WAREHOUSE_ID)))
                .thenReturn(false);
        InventoryLotStatusChangeRequest request =
                new InventoryLotStatusChangeRequest(WAREHOUSE_ID, LotStatus.HOLD, "Found damage", null, null);

        assertThatThrownBy(() -> lotService.changeStatus(LOT_ID, request, "KEY-1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(lotRepository, movementService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(WAREHOUSE_ID));
    }

    @Test
    void changeStatus_allowedWhenWarehouseScopePresent() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_MOVE"), eq("WAREHOUSE"), eq(WAREHOUSE_ID)))
                .thenReturn(true);
        InventoryLot lot = lot();
        when(lotRepository.findById(LOT_ID)).thenReturn(Optional.of(lot));
        StockBalance balance = StockBalance.builder()
                .item(lot.getItem())
                .warehouse(Warehouse.builder()
                        .warehouseId(WAREHOUSE_ID)
                        .plant(Plant.builder()
                                .plantId(UUID.randomUUID())
                                .company(lot.getItem().getCompany())
                                .code("P1").name("Plant 1")
                                .status(OrganizationStatus.ACTIVE)
                                .build())
                        .code("WH1").name("Warehouse 1")
                        .type(WarehouseType.RAW_MATERIAL)
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .lot(lot)
                .quantity(BigDecimal.TEN)
                .build();
        when(balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                lot.getItem().getItemId(), WAREHOUSE_ID, LOT_ID)).thenReturn(Optional.of(balance));
        when(movementService.changeLotStatus(any(), eq("KEY-1"))).thenReturn(
                new InventoryMovementResult(StockMovement.builder()
                        .movementId(UUID.randomUUID())
                        .item(lot.getItem())
                        .warehouse(balance.getWarehouse())
                        .movementType(MovementType.RECEIVE)
                        .direction(MovementDirection.IN)
                        .quantity(BigDecimal.ONE)
                        .createdAt(Instant.now())
                        .build(), true));
        InventoryLotStatusChangeRequest request =
                new InventoryLotStatusChangeRequest(WAREHOUSE_ID, LotStatus.HOLD, "Found damage", null, null);

        assertThatCode(() -> lotService.changeStatus(LOT_ID, request, "KEY-1")).doesNotThrowAnyException();
    }

    private InventoryLot lot() {
        return InventoryLot.builder()
                .lotId(LOT_ID)
                .item(Item.builder()
                        .itemId(UUID.randomUUID())
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
                        .status(ItemStatus.ACTIVE)
                        .build())
                .lotCode("LOT-1")
                .status(LotStatus.AVAILABLE)
                .build();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        InventoryLotService inventoryLotService(InventoryLotRepository lotRepository,
                                                 StockBalanceRepository balanceRepository,
                                                 StockMovementRepository movementRepository,
                                                 InventoryMovementService movementService,
                                                 LotQcOriginLookupService lotQcOriginLookupService,
                                                 InventoryMapper mapper) {
            return new InventoryLotService(
                    lotRepository, balanceRepository, movementRepository, movementService,
                    lotQcOriginLookupService, mapper);
        }

        @Bean
        InventoryMapper inventoryMapper() {
            return new InventoryMapper();
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
        InventoryLotRepository inventoryLotRepository() {
            return mock(InventoryLotRepository.class);
        }

        @Bean
        StockBalanceRepository stockBalanceRepository() {
            return mock(StockBalanceRepository.class);
        }

        @Bean
        StockMovementRepository stockMovementRepository() {
            return mock(StockMovementRepository.class);
        }

        @Bean
        InventoryMovementService inventoryMovementService() {
            return mock(InventoryMovementService.class);
        }

        @Bean
        LotQcOriginLookupService lotQcOriginLookupService() {
            return mock(LotQcOriginLookupService.class);
        }
    }
}
