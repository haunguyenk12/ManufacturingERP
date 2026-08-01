package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseRequisitionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PurchaseOrderMethodSecurityTest.Config.class)
@DisplayName("PurchaseOrderService method security")
class PurchaseOrderMethodSecurityTest {

    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired PurchasingPermissionGuard purchasingPermissionGuard;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, purchasingPermissionGuard, purchaseOrderRepository, organizationLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_deniedWhenPurchaseOrderReadMissing() {
        UUID purchaseOrderId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasOrderAccess(any(), eq("PERM_PURCHASE_ORDER_READ"), eq(purchaseOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> purchaseOrderService.get(purchaseOrderId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(purchaseOrderRepository);
        verify(purchasingPermissionGuard).hasOrderAccess(any(), eq("PERM_PURCHASE_ORDER_READ"), eq(purchaseOrderId));
    }

    @Test
    void cancel_deniedWhenPurchaseOrderManageMissing() {
        UUID purchaseOrderId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasOrderAccess(any(), eq("PERM_PURCHASE_ORDER_MANAGE"), eq(purchaseOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> purchaseOrderService.cancel(purchaseOrderId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(purchaseOrderRepository);
        verify(purchasingPermissionGuard).hasOrderAccess(any(), eq("PERM_PURCHASE_ORDER_MANAGE"), eq(purchaseOrderId));
    }

    @Test
    void list_deniedWhenPlantReadScopeMissing() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_PURCHASE_ORDER_READ"), eq("PLANT"), eq(plantId)))
                .thenReturn(false);

        assertThatThrownBy(() -> purchaseOrderService.list(
                companyId, plantId, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(purchaseOrderRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_PURCHASE_ORDER_READ"), eq("PLANT"), eq(plantId));
    }

    @Test
    void list_allowedWhenPlantReadScopePresent() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        Company company = Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_PURCHASE_ORDER_READ"), eq("PLANT"), eq(plantId)))
                .thenReturn(true);
        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(Plant.builder()
                .plantId(plantId)
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build());
        when(purchaseOrderRepository.search(eq(companyId), eq(plantId), isNull(), isNull(), isNull(),
                any(Pageable.class))).thenReturn(Page.empty());

        assertThatCode(() -> purchaseOrderService.list(
                companyId, plantId, null, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        PurchaseOrderService purchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                                  PurchaseRequisitionRepository purchaseRequisitionRepository,
                                                  OrganizationLookupService organizationLookupService,
                                                  ItemLookupService itemLookupService,
                                                  SupplierService supplierService,
                                                  PurchasingMapper mapper) {
            return new PurchaseOrderService(
                    purchaseOrderRepository, purchaseRequisitionRepository, organizationLookupService,
                    itemLookupService, supplierService, mapper);
        }

        @Bean PurchasingMapper purchasingMapper() { return new PurchasingMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "purchasingPermissionGuard")
        PurchasingPermissionGuard purchasingPermissionGuard() { return mock(PurchasingPermissionGuard.class); }

        @Bean PurchaseOrderRepository purchaseOrderRepository() { return mock(PurchaseOrderRepository.class); }
        @Bean PurchaseRequisitionRepository purchaseRequisitionRepository() { return mock(PurchaseRequisitionRepository.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
        @Bean ItemLookupService itemLookupService() { return mock(ItemLookupService.class); }
        @Bean SupplierService supplierService() { return mock(SupplierService.class); }
    }
}
