package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import com.erp.manufacturing.module.bom.repository.BomLineRepository;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
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
@DisplayName("BomPermissionGuard tests")
class BomPermissionGuardTest {

    @Mock BomHeaderRepository bomHeaderRepository;
    @Mock BomLineRepository bomLineRepository;
    @Mock ItemLookupService itemLookupService;
    @Mock PermissionGuard permissionGuard;

    BomPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new BomPermissionGuard(bomHeaderRepository, bomLineRepository, itemLookupService, permissionGuard);
    }

    @Test
    void hasBomAccess_delegatesToCompanyScope() {
        UUID bomId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        when(bomHeaderRepository.findById(bomId)).thenReturn(Optional.of(bom(bomId, companyId)));
        when(permissionGuard.hasResourceAccess(auth, "PERM_BOM_READ", "COMPANY", companyId)).thenReturn(true);

        assertThat(guard.hasBomAccess(auth, "PERM_BOM_READ", bomId)).isTrue();
    }

    @Test
    void hasBomAccess_missingBomDenied() {
        UUID bomId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        when(bomHeaderRepository.findById(bomId)).thenReturn(Optional.empty());

        assertThat(guard.hasBomAccess(auth, "PERM_BOM_READ", bomId)).isFalse();

        verifyNoInteractions(permissionGuard);
    }

    private BomHeader bom(UUID bomId, UUID companyId) {
        Company company = Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Item parent = Item.builder()
                .itemId(UUID.randomUUID())
                .company(company)
                .code("FG-100")
                .name("FG-100")
                .type(ItemType.FINISHED_GOOD)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
        return BomHeader.builder()
                .bomId(bomId)
                .company(company)
                .parentItem(parent)
                .revision("R1")
                .status(BomStatus.DRAFT)
                .build();
    }
}
