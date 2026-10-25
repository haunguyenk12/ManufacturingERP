package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.sales.dto.SalesOrderCommercialPricesRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderLinePriceRequest;
import com.erp.manufacturing.module.sales.repository.SalesOrderPriceEventRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** G10a: setting prices needs {@code PERM_SALES_PRICE_MANAGE} on the order — not the order's MANAGE. */
@SpringJUnitConfig(SalesOrderPricingMethodSecurityTest.Config.class)
class SalesOrderPricingMethodSecurityTest {

    @Autowired SalesOrderPricingService service;
    @Autowired SalesPermissionGuard salesPermissionGuard;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired CommercialIdempotency idempotency;

    private final UUID orderId = UUID.randomUUID();
    private final SalesOrderCommercialPricesRequest request = new SalesOrderCommercialPricesRequest(0L,
            List.of(new SalesOrderLinePriceRequest(UUID.randomUUID(), BigDecimal.ONE)));

    @BeforeEach
    void setUp() {
        reset(salesPermissionGuard, salesOrderRepository, idempotency);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setPrices_deniedWithoutSalesPriceManage_evenWithSalesOrderManage() {
        when(salesPermissionGuard.hasOrderAccess(any(), eq("PERM_SALES_ORDER_MANAGE"), eq(orderId))).thenReturn(true);
        when(salesPermissionGuard.hasOrderAccess(any(), eq("PERM_SALES_PRICE_MANAGE"), eq(orderId))).thenReturn(false);

        assertThatThrownBy(() -> service.setPrices(orderId, request, "k"))
                .isInstanceOf(AccessDeniedException.class);

        verify(salesPermissionGuard).hasOrderAccess(any(), eq("PERM_SALES_PRICE_MANAGE"), eq(orderId));
        verifyNoInteractions(salesOrderRepository, idempotency);
    }

    @Test
    void setPrices_allowedWithSalesPriceManage_reachesTheService() {
        when(salesPermissionGuard.hasOrderAccess(any(), eq("PERM_SALES_PRICE_MANAGE"), eq(orderId))).thenReturn(true);

        assertThatThrownBy(() -> service.setPrices(orderId, request, "k"))
                .isNotInstanceOf(AccessDeniedException.class);

        verify(idempotency).key("k");
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        SalesOrderPricingService salesOrderPricingService(SalesOrderRepository salesOrderRepository,
                                                          CommercialIdempotency idempotency) {
            return new SalesOrderPricingService(salesOrderRepository, mock(SalesOrderPriceEventRepository.class),
                    idempotency);
        }

        @Bean(name = "salesPermissionGuard")
        SalesPermissionGuard salesPermissionGuard() { return mock(SalesPermissionGuard.class); }

        @Bean SalesOrderRepository salesOrderRepository() { return mock(SalesOrderRepository.class); }

        @Bean CommercialIdempotency commercialIdempotency() { return mock(CommercialIdempotency.class); }
    }
}
