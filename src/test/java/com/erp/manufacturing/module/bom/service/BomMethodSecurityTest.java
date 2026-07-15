package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.bom.dto.BomCreateRequest;
import com.erp.manufacturing.module.bom.mapper.BomMapper;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import com.erp.manufacturing.module.bom.repository.BomLineRepository;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
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

import java.util.UUID;

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
