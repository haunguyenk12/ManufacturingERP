package com.erp.manufacturing.module.costing.service;

import com.erp.manufacturing.module.costing.dto.ItemStandardCostRequest;
import com.erp.manufacturing.module.costing.mapper.CostingMapper;
import com.erp.manufacturing.module.costing.repository.ItemStandardCostRepository;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
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
 * Rule R2: every deny branch pairs with {@code verify(...)} pinning the exact permission string,
 * plus at least one allow branch, same shape as {@code WorkCenterMethodSecurityTest}.
 */
@SpringJUnitConfig(ItemStandardCostMethodSecurityTest.Config.class)
@DisplayName("ItemStandardCostService method security")
class ItemStandardCostMethodSecurityTest {

    @Autowired ItemStandardCostService itemStandardCostService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired ItemStandardCostRepository repository;
    @Autowired ItemLookupService itemLookupService;

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, repository, itemLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Item activeItem() {
        return Item.builder().itemId(ITEM_ID)
                .company(Company.builder().companyId(COMPANY_ID).code("ACME").name("ACME")
                        .status(OrganizationStatus.ACTIVE).build())
                .code("RM-001").name("Raw Material").type(ItemType.RAW_MATERIAL).unit("EA")
                .status(ItemStatus.ACTIVE).build();
    }

    @Test
    void upsert_deniedWhenCostingManageScopeMissing() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_COSTING_MANAGE"), eq("COMPANY"), eq(COMPANY_ID))).thenReturn(false);

        assertThatThrownBy(() -> itemStandardCostService.upsert(COMPANY_ID, ITEM_ID,
                new ItemStandardCostRequest(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository, itemLookupService);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_COSTING_MANAGE"), eq("COMPANY"), eq(COMPANY_ID));
    }

    @Test
    void upsert_allowedWhenCostingManageScopePresent() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_COSTING_MANAGE"), eq("COMPANY"), eq(COMPANY_ID))).thenReturn(true);
        when(itemLookupService.getActiveItem(ITEM_ID)).thenReturn(activeItem());
        when(repository.findByItemItemId(ITEM_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> itemStandardCostService.upsert(COMPANY_ID, ITEM_ID,
                new ItemStandardCostRequest(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)))
                .doesNotThrowAnyException();
    }

    @Test
    void get_deniedWhenCostingReadScopeMissing() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_COSTING_READ"), eq("COMPANY"), eq(COMPANY_ID))).thenReturn(false);

        assertThatThrownBy(() -> itemStandardCostService.get(COMPANY_ID, ITEM_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository, itemLookupService);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_COSTING_READ"), eq("COMPANY"), eq(COMPANY_ID));
    }

    @Test
    void list_deniedWhenCostingReadScopeMissing() {
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_COSTING_READ"), eq("COMPANY"), eq(COMPANY_ID))).thenReturn(false);

        assertThatThrownBy(() -> itemStandardCostService.list(COMPANY_ID, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_COSTING_READ"), eq("COMPANY"), eq(COMPANY_ID));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        ItemStandardCostService itemStandardCostService(ItemStandardCostRepository repository,
                                                         ItemLookupService itemLookupService,
                                                         CostingService costingService,
                                                         CostingMapper mapper) {
            return new ItemStandardCostService(repository, itemLookupService, costingService, mapper);
        }

        @Bean CostingMapper costingMapper() { return new CostingMapper(); }

        @Bean
        CostingService costingService(ItemStandardCostRepository repository,
                                      com.erp.manufacturing.module.bom.service.BomLookupService bomLookupService) {
            return new CostingService(repository, bomLookupService);
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean ItemStandardCostRepository itemStandardCostRepository() { return mock(ItemStandardCostRepository.class); }
        @Bean ItemLookupService itemLookupService() { return mock(ItemLookupService.class); }
        @Bean com.erp.manufacturing.module.bom.service.BomLookupService bomLookupService() {
            return mock(com.erp.manufacturing.module.bom.service.BomLookupService.class);
        }
    }
}
