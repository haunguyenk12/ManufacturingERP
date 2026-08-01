package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionRepository;
import com.erp.manufacturing.module.planning.service.MrpPlanningPermissionGuard;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
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

@SpringJUnitConfig(PurchaseRequisitionMethodSecurityTest.Config.class)
@DisplayName("PurchaseRequisitionService method security")
class PurchaseRequisitionMethodSecurityTest {

    @Autowired PurchaseRequisitionService requisitionService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired PurchasingPermissionGuard purchasingPermissionGuard;
    @Autowired MrpPlanningPermissionGuard mrpPlanningPermissionGuard;
    @Autowired PurchaseRequisitionRepository requisitionRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, purchasingPermissionGuard, mrpPlanningPermissionGuard,
                requisitionRepository, organizationLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_deniedWhenRequisitionReadMissing() {
        UUID requisitionId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasRequisitionAccess(
                any(), eq("PERM_PURCHASE_REQUISITION_READ"), eq(requisitionId))).thenReturn(false);

        assertThatThrownBy(() -> requisitionService.get(requisitionId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(requisitionRepository);
        verify(purchasingPermissionGuard).hasRequisitionAccess(
                any(), eq("PERM_PURCHASE_REQUISITION_READ"), eq(requisitionId));
    }

    @Test
    void approve_deniedWhenRequisitionManageMissing() {
        UUID requisitionId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasRequisitionAccess(
                any(), eq("PERM_PURCHASE_REQUISITION_MANAGE"), eq(requisitionId))).thenReturn(false);

        assertThatThrownBy(() -> requisitionService.approve(requisitionId, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(requisitionRepository);
        verify(purchasingPermissionGuard).hasRequisitionAccess(
                any(), eq("PERM_PURCHASE_REQUISITION_MANAGE"), eq(requisitionId));
    }

    @Test
    void convertFromSuggestion_deniedWhenRequisitionManageMissing() {
        UUID suggestionId = UUID.randomUUID();
        when(mrpPlanningPermissionGuard.hasSuggestionAccess(
                any(), eq("PERM_PURCHASE_REQUISITION_MANAGE"), eq(suggestionId))).thenReturn(false);

        assertThatThrownBy(() -> requisitionService.convertFromSuggestion(suggestionId, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(requisitionRepository);
        verify(mrpPlanningPermissionGuard).hasSuggestionAccess(
                any(), eq("PERM_PURCHASE_REQUISITION_MANAGE"), eq(suggestionId));
    }

    @Test
    void list_deniedWhenPlantReadScopeMissing() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_PURCHASE_REQUISITION_READ"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> requisitionService.list(companyId, plantId, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(requisitionRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_PURCHASE_REQUISITION_READ"), eq("PLANT"), eq(plantId));
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
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_PURCHASE_REQUISITION_READ"), eq("PLANT"), eq(plantId))).thenReturn(true);
        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(Plant.builder()
                .plantId(plantId)
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build());
        when(requisitionRepository.search(eq(companyId), eq(plantId), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> requisitionService.list(companyId, plantId, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        PurchaseRequisitionService purchaseRequisitionService(PurchaseRequisitionRepository requisitionRepository,
                                                              SupplySuggestionRepository supplySuggestionRepository,
                                                              OrganizationLookupService organizationLookupService,
                                                              ItemLookupService itemLookupService,
                                                              SupplierService supplierService,
                                                              PurchaseOrderService purchaseOrderService,
                                                              PurchasingMapper mapper) {
            return new PurchaseRequisitionService(
                    requisitionRepository, supplySuggestionRepository, organizationLookupService,
                    itemLookupService, supplierService, purchaseOrderService, mapper);
        }

        @Bean PurchasingMapper purchasingMapper() { return new PurchasingMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "purchasingPermissionGuard")
        PurchasingPermissionGuard purchasingPermissionGuard() { return mock(PurchasingPermissionGuard.class); }

        @Bean(name = "mrpPlanningPermissionGuard")
        MrpPlanningPermissionGuard mrpPlanningPermissionGuard() { return mock(MrpPlanningPermissionGuard.class); }

        @Bean PurchaseRequisitionRepository purchaseRequisitionRepository() { return mock(PurchaseRequisitionRepository.class); }
        @Bean SupplySuggestionRepository supplySuggestionRepository() { return mock(SupplySuggestionRepository.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
        @Bean ItemLookupService itemLookupService() { return mock(ItemLookupService.class); }
        @Bean SupplierService supplierService() { return mock(SupplierService.class); }
        @Bean PurchaseOrderService purchaseOrderService() { return mock(PurchaseOrderService.class); }
    }
}
