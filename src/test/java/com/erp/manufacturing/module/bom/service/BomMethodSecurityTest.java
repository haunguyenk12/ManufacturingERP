package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.dto.BomCreateRequest;
import com.erp.manufacturing.module.bom.mapper.BomMapper;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import com.erp.manufacturing.module.bom.repository.BomLineRepository;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
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

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(BomMethodSecurityTest.Config.class)
@DisplayName("BomService method security")
class BomMethodSecurityTest {

    @Autowired BomService bomService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired BomPermissionGuard bomPermissionGuard;
    @Autowired BomHeaderRepository bomHeaderRepository;
    @Autowired ItemLookupService itemLookupService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, bomPermissionGuard, bomHeaderRepository, itemLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getBom_deniedWhenGuardDenies() {
        UUID bomId = UUID.randomUUID();
        when(bomPermissionGuard.hasBomAccess(any(), eq("PERM_BOM_READ"), eq(bomId))).thenReturn(false);

        assertThatThrownBy(() -> bomService.getBom(bomId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(bomHeaderRepository);
        verify(bomPermissionGuard).hasBomAccess(any(), eq("PERM_BOM_READ"), eq(bomId));
    }

    @Test
    void getBom_allowedWhenGuardAllows() {
        UUID bomId = UUID.randomUUID();
        when(bomPermissionGuard.hasBomAccess(any(), eq("PERM_BOM_READ"), eq(bomId))).thenReturn(true);
        when(bomHeaderRepository.findWithLinesByBomId(bomId)).thenReturn(Optional.of(bom(bomId)));

        assertThatCode(() -> bomService.getBom(bomId)).doesNotThrowAnyException();
    }

    @Test
    void createBom_deniedWhenCompanyScopeMissing() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_BOM_MANAGE"), eq("COMPANY"), eq(companyId)))
                .thenReturn(false);

        assertThatThrownBy(() -> bomService.createBom(companyId, new BomCreateRequest(itemId, "R1", null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(itemLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_BOM_MANAGE"), eq("COMPANY"), eq(companyId));
    }

    private BomHeader bom(UUID bomId) {
        Company company = Company.builder()
                .companyId(UUID.randomUUID())
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Item parent = Item.builder()
                .itemId(UUID.randomUUID())
                .company(company)
                .code("FG-100")
                .name("Finished Good")
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
                .lines(new ArrayList<>())
                .build();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        BomService bomService(BomHeaderRepository bomHeaderRepository,
                              BomLineRepository bomLineRepository,
                              ItemLookupService itemLookupService,
                              BomMapper mapper) {
            return new BomService(bomHeaderRepository, bomLineRepository, itemLookupService, mapper);
        }

        @Bean
        BomMapper bomMapper() {
            return new BomMapper();
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean(name = "bomPermissionGuard")
        BomPermissionGuard bomPermissionGuard() {
            return mock(BomPermissionGuard.class);
        }

        @Bean
        BomHeaderRepository bomHeaderRepository() {
            return mock(BomHeaderRepository.class);
        }

        @Bean
        BomLineRepository bomLineRepository() {
            return mock(BomLineRepository.class);
        }

        @Bean
        ItemLookupService itemLookupService() {
            return mock(ItemLookupService.class);
        }
    }
}
