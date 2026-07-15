package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
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
    @Mock PermissionGuard permissionGuard;

    InventoryPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new InventoryPermissionGuard(itemRepository, permissionGuard);
    }

    @Test
    void hasItemAccess_delegatesToCompanyScope() {
        UUID itemId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item(itemId, companyId)));
        when(permissionGuard.hasResourceAccess(auth, "PERM_INVENTORY_READ", "COMPANY", companyId))
                .thenReturn(true);

        assertThat(guard.hasItemAccess(auth, "PERM_INVENTORY_READ", itemId)).isTrue();
    }

    @Test
    void hasItemAccess_missingItemDenied() {
        UUID itemId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThat(guard.hasItemAccess(auth, "PERM_INVENTORY_READ", itemId)).isFalse();

        verifyNoInteractions(permissionGuard);
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
