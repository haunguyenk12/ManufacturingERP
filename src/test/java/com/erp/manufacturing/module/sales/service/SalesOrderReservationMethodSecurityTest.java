package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderReservationReleaseRequest;
import com.erp.manufacturing.module.sales.mapper.SalesOrderMapper;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.SalesOrderCoverageLookupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FE Q4: holding stock for an order needs {@code PERM_SALES_ORDER_MANAGE} on the order (not the work
 * order reservation permission); reading holds and candidates needs {@code PERM_SALES_ORDER_READ}.
 */
@SpringJUnitConfig(SalesOrderReservationMethodSecurityTest.Config.class)
class SalesOrderReservationMethodSecurityTest {

    @Autowired SalesOrderReservationService service;
    @Autowired SalesPermissionGuard salesPermissionGuard;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderStockReservationService stockReservationService;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(salesPermissionGuard, salesOrderRepository, stockReservationService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reserve_deniedWithoutSalesOrderManage() {
        when(salesPermissionGuard.hasOrderAccess(any(), eq("PERM_SALES_ORDER_MANAGE"), eq(orderId))).thenReturn(false);

        assertThatThrownBy(() -> service.reserve(orderId, new SalesOrderReservationCreateRequest(
                0L, UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.ONE)))
                .isInstanceOf(AccessDeniedException.class);

        verify(salesPermissionGuard).hasOrderAccess(any(), eq("PERM_SALES_ORDER_MANAGE"), eq(orderId));
        verifyNoInteractions(salesOrderRepository, stockReservationService);
    }

    @Test
    void release_deniedWithoutSalesOrderManage() {
        when(salesPermissionGuard.hasOrderAccess(any(), eq("PERM_SALES_ORDER_MANAGE"), eq(orderId))).thenReturn(false);

        assertThatThrownBy(() -> service.release(orderId, UUID.randomUUID(),
                new SalesOrderReservationReleaseRequest(0L, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(salesPermissionGuard).hasOrderAccess(any(), eq("PERM_SALES_ORDER_MANAGE"), eq(orderId));
        verifyNoInteractions(stockReservationService);
    }

    @Test
    void list_deniedWithoutSalesOrderRead() {
        when(salesPermissionGuard.hasOrderAccess(any(), eq("PERM_SALES_ORDER_READ"), eq(orderId))).thenReturn(false);

        assertThatThrownBy(() -> service.list(orderId, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(salesPermissionGuard).hasOrderAccess(any(), eq("PERM_SALES_ORDER_READ"), eq(orderId));
        verifyNoInteractions(stockReservationService);
    }

    /** Allow branch: with READ the guard lets the call through to the service body. */
    @Test
    void list_allowedWithSalesOrderRead_reachesTheService() {
        when(salesPermissionGuard.hasOrderAccess(any(), eq("PERM_SALES_ORDER_READ"), eq(orderId))).thenReturn(true);

        assertThatThrownBy(() -> service.list(orderId, null, null, PageRequest.of(0, 20)))
                .isNotInstanceOf(AccessDeniedException.class);

        verify(salesOrderRepository).findWithDetailsBySalesOrderId(orderId);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        SalesOrderReservationService salesOrderReservationService(SalesOrderRepository salesOrderRepository,
                                                                  SalesOrderStockReservationService stockReservationService) {
            SalesOrderCoverageLookupService coverage = mock(SalesOrderCoverageLookupService.class);
            return new SalesOrderReservationService(salesOrderRepository, mock(OrganizationLookupService.class),
                    stockReservationService,
                    new SalesOrderDetailAssembler(stockReservationService, coverage, new SalesOrderMapper(), org.mockito.Mockito.mock(SalesPriceVisibility.class), org.mockito.Mockito.mock(com.erp.manufacturing.module.delivery.service.DeliveryLookupService.class)));
        }

        @Bean(name = "salesPermissionGuard")
        SalesPermissionGuard salesPermissionGuard() { return mock(SalesPermissionGuard.class); }

        @Bean SalesOrderRepository salesOrderRepository() { return mock(SalesOrderRepository.class); }

        @Bean SalesOrderStockReservationService stockReservationService() {
            return mock(SalesOrderStockReservationService.class);
        }
    }
}
