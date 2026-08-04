package com.erp.manufacturing.module.routing.service;

import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.dto.RoutingCreateRequest;
import com.erp.manufacturing.module.routing.dto.RoutingOperationRequest;
import com.erp.manufacturing.module.routing.mapper.RoutingMapper;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(RoutingMethodSecurityTest.Config.class)
@DisplayName("RoutingService method security")
class RoutingMethodSecurityTest {

    @Autowired RoutingService routingService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired RoutingPermissionGuard routingPermissionGuard;
    @Autowired RoutingHeaderRepository routingHeaderRepository;
    @Autowired ItemLookupService itemLookupService;
    @Autowired WorkCenterLookupService workCenterLookupService;

    private static final UUID WORK_CENTER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, routingPermissionGuard, routingHeaderRepository, itemLookupService, workCenterLookupService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
        lenient().when(workCenterLookupService.getActiveWorkCenter(WORK_CENTER_ID)).thenReturn(
                WorkCenter.builder().workCenterId(WORK_CENTER_ID)
                        .plant(Plant.builder().plantId(UUID.randomUUID()).code("PLANT").name("Plant")
                                .status(OrganizationStatus.ACTIVE).build())
                        .code("WC-01").name("WC-01")
                        .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                        .status(OrganizationStatus.ACTIVE).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_deniedWhenCompanyManageScopeMissing() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_ROUTING_MANAGE"), eq("COMPANY"), eq(companyId))).thenReturn(false);

        assertThatThrownBy(() -> routingService.create(companyId, new RoutingCreateRequest(
                UUID.randomUUID(), "RT-FG100", "V1", null,
                List.of(new RoutingOperationRequest(10, "Assembly", WORK_CENTER_ID,
                        BigDecimal.ZERO, BigDecimal.ONE)))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(routingHeaderRepository, itemLookupService);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_ROUTING_MANAGE"), eq("COMPANY"), eq(companyId));
    }

    @Test
    void activate_deniedWhenRoutingManageScopeMissing() {
        UUID routingId = UUID.randomUUID();
        when(routingPermissionGuard.hasRoutingAccess(
                any(), eq("PERM_ROUTING_MANAGE"), eq(routingId))).thenReturn(false);

        assertThatThrownBy(() -> routingService.activate(routingId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(routingHeaderRepository);
        verify(routingPermissionGuard).hasRoutingAccess(any(), eq("PERM_ROUTING_MANAGE"), eq(routingId));
    }

    @Test
    void deactivate_deniedWhenRoutingManageScopeMissing() {
        UUID routingId = UUID.randomUUID();
        when(routingPermissionGuard.hasRoutingAccess(
                any(), eq("PERM_ROUTING_MANAGE"), eq(routingId))).thenReturn(false);

        assertThatThrownBy(() -> routingService.deactivate(routingId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(routingHeaderRepository);
        verify(routingPermissionGuard).hasRoutingAccess(any(), eq("PERM_ROUTING_MANAGE"), eq(routingId));
    }

    @Test
    void get_deniedWhenRoutingReadScopeMissing() {
        UUID routingId = UUID.randomUUID();
        when(routingPermissionGuard.hasRoutingAccess(
                any(), eq("PERM_ROUTING_READ"), eq(routingId))).thenReturn(false);

        assertThatThrownBy(() -> routingService.get(routingId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(routingHeaderRepository);
        verify(routingPermissionGuard).hasRoutingAccess(any(), eq("PERM_ROUTING_READ"), eq(routingId));
    }

    @Test
    void list_deniedWhenCompanyReadScopeMissing() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_ROUTING_READ"), eq("COMPANY"), eq(companyId))).thenReturn(false);

        assertThatThrownBy(() -> routingService.list(companyId, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(routingHeaderRepository);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_ROUTING_READ"), eq("COMPANY"), eq(companyId));
    }

    @Test
    void list_allowedWhenCompanyReadScopePresent() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_ROUTING_READ"), eq("COMPANY"), eq(companyId))).thenReturn(true);
        when(routingHeaderRepository.search(eq(companyId), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> routingService.list(companyId, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void list_allowedWithStatusFilter() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_ROUTING_READ"), eq("COMPANY"), eq(companyId))).thenReturn(true);
        when(routingHeaderRepository.search(
                eq(companyId), eq(itemId), eq(RoutingStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> routingService.list(companyId, itemId, RoutingStatus.ACTIVE, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        RoutingService routingService(RoutingHeaderRepository routingHeaderRepository,
                                      ItemLookupService itemLookupService,
                                      WorkCenterLookupService workCenterLookupService,
                                      RoutingMapper mapper) {
            return new RoutingService(routingHeaderRepository, itemLookupService, workCenterLookupService, mapper);
        }

        @Bean RoutingMapper routingMapper() { return new RoutingMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "routingPermissionGuard")
        RoutingPermissionGuard routingPermissionGuard() { return mock(RoutingPermissionGuard.class); }

        @Bean RoutingHeaderRepository routingHeaderRepository() { return mock(RoutingHeaderRepository.class); }
        @Bean ItemLookupService itemLookupService() { return mock(ItemLookupService.class); }
        @Bean WorkCenterLookupService workCenterLookupService() { return mock(WorkCenterLookupService.class); }
    }
}
