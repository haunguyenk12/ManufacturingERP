package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.inventory.repository.StockTransferRepository;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryPermissionGuard tests")
class InventoryPermissionGuardTest {

    @Mock ItemRepository itemRepository;
    @Mock InventoryLotRepository lotRepository;
    @Mock StockBalanceRepository balanceRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock StockTransferRepository transferRepository;
    @Mock PermissionGuard permissionGuard;

    InventoryPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new InventoryPermissionGuard(itemRepository, lotRepository, balanceRepository,
                movementRepository, transferRepository, permissionGuard);
    }

    @Test
    void hasItemAccess_delegatesToCompanyOrPlantScope() {
        UUID itemId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item(itemId, companyId)));
        when(permissionGuard.hasCompanyOrPlantAccess(auth, "PERM_ITEM_READ", companyId))
                .thenReturn(true);

        assertThat(guard.hasItemAccess(auth, "PERM_ITEM_READ", itemId)).isTrue();
    }

    @Test
    void hasItemAccess_missingItemDenied() {
        UUID itemId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThat(guard.hasItemAccess(auth, "PERM_INVENTORY_READ", itemId)).isFalse();

        verifyNoInteractions(permissionGuard);
    }

    @Test
    void hasItemCompanyAccess_delegatesToCompanyOrPlantScope() {
        UUID companyId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        when(permissionGuard.hasCompanyOrPlantAccess(auth, "PERM_ITEM_MANAGE", companyId))
                .thenReturn(true);

        assertThat(guard.hasItemCompanyAccess(auth, "PERM_ITEM_MANAGE", companyId)).isTrue();
    }

    @Test
    void hasLotCompanyAccess_delegatesToCompanyScopeViaItem() {
        UUID lotId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(lot(itemId, companyId)));
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_READ", "COMPANY", companyId))
                .thenReturn(true);

        assertThat(guard.hasLotCompanyAccess(auth, "PERM_INVENTORY_READ", lotId)).isTrue();
    }

    @Test
    void hasLotCompanyAccess_missingLotDenied() {
        UUID lotId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        when(lotRepository.findById(lotId)).thenReturn(Optional.empty());

        assertThat(guard.hasLotCompanyAccess(auth, "PERM_INVENTORY_READ", lotId)).isFalse();

        verifyNoInteractions(permissionGuard);
    }

    // ── G4-1: lot status acts on every warehouse holding the lot (B149) ────

    @Test
    void hasLotPositionsAccess_allWarehousesOfTheLotInScope_allows() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        UUID lotId = UUID.randomUUID();
        UUID requested = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_MOVE", "WAREHOUSE", requested)).thenReturn(true);
        when(balanceRepository.findWarehouseIdsHoldingLot(lotId)).thenReturn(java.util.List.of(requested, other));
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_MOVE", "WAREHOUSE", other)).thenReturn(true);

        assertThat(guard.hasLotPositionsAccess(auth, "PERM_INVENTORY_MOVE", lotId, requested)).isTrue();
    }

    @Test
    void hasLotPositionsAccess_oneWarehouseOfTheLotOutOfScope_denies() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        UUID lotId = UUID.randomUUID();
        UUID requested = UUID.randomUUID();
        UUID outOfScope = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_MOVE", "WAREHOUSE", requested)).thenReturn(true);
        when(balanceRepository.findWarehouseIdsHoldingLot(lotId)).thenReturn(java.util.List.of(requested, outOfScope));
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_MOVE", "WAREHOUSE", outOfScope)).thenReturn(false);

        assertThat(guard.hasLotPositionsAccess(auth, "PERM_INVENTORY_MOVE", lotId, requested)).isFalse();
    }

    @Test
    void hasLotPositionsAccess_requestedWarehouseOutOfScope_deniesWithoutReadingPositions() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        UUID requested = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_MOVE", "WAREHOUSE", requested)).thenReturn(false);

        assertThat(guard.hasLotPositionsAccess(auth, "PERM_INVENTORY_MOVE", UUID.randomUUID(), requested)).isFalse();
        verifyNoInteractions(balanceRepository);
    }

    // ── G4-4 / G4-2: detail guards ─────────────────────────────────────────

    @Test
    void hasMovementAccess_checksTheWarehouseTheMovementWasPostedIn() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        UUID movementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(movementRepository.findWarehouseIdByMovementId(movementId)).thenReturn(Optional.of(warehouseId));
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_READ", "WAREHOUSE", warehouseId)).thenReturn(true);

        assertThat(guard.hasMovementAccess(auth, "PERM_INVENTORY_READ", movementId)).isTrue();
        assertThat(guard.hasMovementAccess(auth, "PERM_INVENTORY_READ", UUID.randomUUID())).isFalse();
    }

    @Test
    void hasTransferAccess_eitherSideInScope_allows_neither_denies() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        com.erp.manufacturing.module.inventory.domain.StockTransfer transfer =
                com.erp.manufacturing.module.inventory.domain.StockTransfer.builder()
                        .transferId(UUID.randomUUID())
                        .fromWarehouse(com.erp.manufacturing.module.organization.domain.Warehouse.builder()
                                .warehouseId(fromId).build())
                        .toWarehouse(com.erp.manufacturing.module.organization.domain.Warehouse.builder()
                                .warehouseId(toId).build())
                        .build();
        when(transferRepository.findById(transfer.getTransferId())).thenReturn(Optional.of(transfer));
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_READ", "WAREHOUSE", fromId)).thenReturn(false);
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_READ", "WAREHOUSE", toId)).thenReturn(true, false);

        assertThat(guard.hasTransferAccess(auth, "PERM_INVENTORY_READ", transfer.getTransferId())).isTrue();
        assertThat(guard.hasTransferAccess(auth, "PERM_INVENTORY_READ", transfer.getTransferId())).isFalse();
    }

    private InventoryLot lot(UUID itemId, UUID companyId) {
        return InventoryLot.builder()
                .lotId(UUID.randomUUID())
                .item(item(itemId, companyId))
                .lotCode("LOT-1")
                .status(LotStatus.AVAILABLE)
                .build();
    }

    private Item item(UUID itemId, UUID companyId) {
        return Item.builder()
                .itemId(itemId)
                .company(Company.builder()
                        .companyId(companyId)
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
                .build();
    }
}
