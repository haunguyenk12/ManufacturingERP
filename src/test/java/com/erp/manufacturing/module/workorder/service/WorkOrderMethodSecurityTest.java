package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.dto.execution.WorkOrderComponentIssueRequest;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderCreateRequest;
import com.erp.manufacturing.module.workorder.mapper.WorkOrderMapper;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionReceiptService;
import com.erp.manufacturing.module.workorder.service.execution.WipTransactionService;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(WorkOrderMethodSecurityTest.Config.class)
@DisplayName("WorkOrderService method security")
class WorkOrderMethodSecurityTest {

    @Autowired WorkOrderService workOrderService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired WorkOrderPermissionGuard workOrderPermissionGuard;
    @Autowired PlantRepository plantRepository;
    @Autowired WorkOrderRepository workOrderRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, workOrderPermissionGuard, plantRepository, workOrderRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_deniedWhenPlantManageScopeMissing() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_WORK_ORDER_MANAGE"), eq("PLANT"), eq(plantId)))
                .thenReturn(false);

        assertThatThrownBy(() -> workOrderService.create(plantId, new WorkOrderCreateRequest(
                "WO-001", UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(plantRepository);
    }

    @Test
    void get_deniedWhenWorkOrderReadScopeMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_READ"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> workOrderService.get(workOrderId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderRepository);
    }

    @Test
    void issueComponent_deniedWhenExecuteScopeMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_EXECUTE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> workOrderService.issueComponent(workOrderId, new WorkOrderComponentIssueRequest(
                UUID.randomUUID(), UUID.randomUUID(), null, null, BigDecimal.ONE, null), "KEY-1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderRepository);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        WorkOrderService workOrderService(WorkOrderRepository workOrderRepository,
                                          OrganizationLookupService organizationLookupService,
                                          ItemLookupService itemLookupService,
                                          BomLookupService bomLookupService,
                                          MaterialReservationService materialReservationService,
                                          MaterialIssueService materialIssueService,
                                          WipTransactionService wipTransactionService,
                                          ProductionReceiptService productionReceiptService,
                                          WorkOrderMapper mapper) {
            return new WorkOrderService(
                    workOrderRepository,
                    organizationLookupService,
                    itemLookupService,
                    bomLookupService,
                    materialReservationService,
                    materialIssueService,
                    wipTransactionService,
                    productionReceiptService,
                    mapper);
        }

        @Bean
        WorkOrderMapper workOrderMapper() {
            return new WorkOrderMapper();
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean(name = "workOrderPermissionGuard")
        WorkOrderPermissionGuard workOrderPermissionGuard() {
            return mock(WorkOrderPermissionGuard.class);
        }

        @Bean
        WorkOrderRepository workOrderRepository() {
            return mock(WorkOrderRepository.class);
        }

        @Bean
        PlantRepository plantRepository() {
            return mock(PlantRepository.class);
        }

        @Bean
        OrganizationLookupService organizationLookupService() {
            return mock(OrganizationLookupService.class);
        }

        @Bean
        ItemLookupService itemLookupService() {
            return mock(ItemLookupService.class);
        }

        @Bean
        BomLookupService bomLookupService() {
            return mock(BomLookupService.class);
        }

        @Bean
        MaterialReservationService materialReservationService() {
            return mock(MaterialReservationService.class);
        }

        @Bean
        MaterialIssueService materialIssueService() {
            return mock(MaterialIssueService.class);
        }

        @Bean
        WipTransactionService wipTransactionService() {
            return mock(WipTransactionService.class);
        }

        @Bean
        ProductionReceiptService productionReceiptService() {
            return mock(ProductionReceiptService.class);
        }
    }
}
