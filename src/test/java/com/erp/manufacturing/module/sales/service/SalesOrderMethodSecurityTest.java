package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import com.erp.manufacturing.module.uom.service.UomLookupService;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.SalesOrderCoverageLookupService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(SalesOrderMethodSecurityTest.Config.class)
@DisplayName("SalesOrderService method security")
class SalesOrderMethodSecurityTest {

    @Autowired SalesOrderService salesOrderService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired SalesPermissionGuard salesPermissionGuard;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired OrganizationLookupService organizationLookupService;
    @Autowired PlanningDemandService planningDemandService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, salesPermissionGuard, salesOrderRepository, salesOrderLineRepository,
                organizationLookupService, planningDemandService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_deniedWhenSalesOrderReadMissing() {
        UUID salesOrderId = UUID.randomUUID();
        when(salesPermissionGuard.hasOrderAccess(
                any(), eq("PERM_SALES_ORDER_READ"), eq(salesOrderId))).thenReturn(false);

        assertThatThrownBy(() -> salesOrderService.get(salesOrderId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(salesOrderRepository);
        verify(salesPermissionGuard).hasOrderAccess(any(), eq("PERM_SALES_ORDER_READ"), eq(salesOrderId));
    }

    @Test
    void update_deniedWhenSalesOrderManageMissing() {
        UUID salesOrderId = UUID.randomUUID();
        when(salesPermissionGuard.hasOrderAccess(
                any(), eq("PERM_SALES_ORDER_MANAGE"), eq(salesOrderId))).thenReturn(false);

        assertThatThrownBy(() -> salesOrderService.update(salesOrderId,
                new com.erp.manufacturing.module.sales.dto.SalesOrderUpdateRequest(
                        1L, "New Customer", null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(salesOrderRepository, planningDemandService);
        verify(salesPermissionGuard).hasOrderAccess(any(), eq("PERM_SALES_ORDER_MANAGE"), eq(salesOrderId));
    }

    @Test
    void confirm_deniedWhenSalesOrderManageMissing() {
        UUID salesOrderId = UUID.randomUUID();
        when(salesPermissionGuard.hasOrderAccess(
                any(), eq("PERM_SALES_ORDER_MANAGE"), eq(salesOrderId))).thenReturn(false);

        assertThatThrownBy(() -> salesOrderService.confirm(salesOrderId, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(salesOrderRepository, planningDemandService);
        verify(salesPermissionGuard).hasOrderAccess(any(), eq("PERM_SALES_ORDER_MANAGE"), eq(salesOrderId));
    }

    @Test
    void cancel_deniedWhenSalesOrderManageMissing() {
        UUID salesOrderId = UUID.randomUUID();
        when(salesPermissionGuard.hasOrderAccess(
                any(), eq("PERM_SALES_ORDER_MANAGE"), eq(salesOrderId))).thenReturn(false);

        assertThatThrownBy(() -> salesOrderService.cancel(salesOrderId, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(salesOrderRepository, planningDemandService);
        verify(salesPermissionGuard).hasOrderAccess(any(), eq("PERM_SALES_ORDER_MANAGE"), eq(salesOrderId));
    }

    @Test
    void create_deniedWhenPlantManageScopeMissing() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_SALES_ORDER_MANAGE"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> salesOrderService.create(new com.erp.manufacturing.module.sales.dto
                .SalesOrderCreateRequest(companyId, plantId, "SO-001", "ACME", LocalDate.now(), null, List.of())))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(salesOrderRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_SALES_ORDER_MANAGE"), eq("PLANT"), eq(plantId));
    }

    @Test
    void planningDemands_deniedWhenMrpRunScopeMissing() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_MRP_RUN"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> salesOrderService.planningDemands(plantId, LocalDate.now(), false))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(salesOrderLineRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_MRP_RUN"), eq("PLANT"), eq(plantId));
    }

    @Test
    void planningDemands_allowedWhenMrpRunScopePresent() {
        UUID plantId = UUID.randomUUID();
        LocalDate horizonEnd = LocalDate.now().plusDays(30);
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_MRP_RUN"), eq("PLANT"), eq(plantId))).thenReturn(true);
        when(salesOrderLineRepository.findEligiblePlanningDemands(
                eq(plantId), eq(horizonEnd), anyCollection())).thenReturn(List.of());

        assertThatCode(() -> salesOrderService.planningDemands(plantId, horizonEnd, false))
                .doesNotThrowAnyException();
    }

    @Test
    void list_allowedWhenPlantReadScopePresent() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        Company company = Company.builder().companyId(companyId).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_SALES_ORDER_READ"), eq("PLANT"), eq(plantId))).thenReturn(true);
        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company);
        when(organizationLookupService.getActivePlant(plantId)).thenReturn(Plant.builder()
                .plantId(plantId).company(company).code("P1").name("Plant 1")
                .status(OrganizationStatus.ACTIVE).build());
        when(salesOrderRepository.searchExtended(eq(companyId), eq(plantId), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> salesOrderService.list(companyId, plantId, null, null, null, null, null, null,
                PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        SalesOrderService salesOrderService(SalesOrderRepository salesOrderRepository,
                                            SalesOrderLineRepository salesOrderLineRepository,
                                            OrganizationLookupService organizationLookupService,
                                            ItemLookupService itemLookupService,
                                            PlanningDemandService planningDemandService,
                                            SalesOrderMapper mapper) {
            SalesOrderStockReservationService stockReservationService = mock(SalesOrderStockReservationService.class);
            SalesOrderCoverageLookupService coverageLookupService = mock(SalesOrderCoverageLookupService.class);
            return new SalesOrderService(salesOrderRepository, salesOrderLineRepository,
                    mock(CustomerRepository.class), organizationLookupService, itemLookupService,
                    mock(UomLookupService.class), planningDemandService, stockReservationService,
                    coverageLookupService,
                    new SalesOrderDetailAssembler(stockReservationService, coverageLookupService, mapper, org.mockito.Mockito.mock(SalesPriceVisibility.class), org.mockito.Mockito.mock(com.erp.manufacturing.module.delivery.service.DeliveryLookupService.class)),
                    mapper, org.mockito.Mockito.mock(com.erp.manufacturing.module.delivery.service.DeliveryLookupService.class));
        }

        @Bean SalesOrderMapper salesOrderMapper() { return new SalesOrderMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "salesPermissionGuard")
        SalesPermissionGuard salesPermissionGuard() { return mock(SalesPermissionGuard.class); }

        @Bean SalesOrderRepository salesOrderRepository() { return mock(SalesOrderRepository.class); }
        @Bean SalesOrderLineRepository salesOrderLineRepository() { return mock(SalesOrderLineRepository.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
        @Bean ItemLookupService itemLookupService() { return mock(ItemLookupService.class); }
        @Bean PlanningDemandService planningDemandService() { return mock(PlanningDemandService.class); }
    }
}
