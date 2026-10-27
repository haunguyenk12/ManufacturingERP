package com.erp.manufacturing.module.delivery.service;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.module.delivery.dto.DeliveryCancelRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryCreateRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryLineRequest;
import com.erp.manufacturing.module.delivery.dto.DeliveryPostRequest;
import com.erp.manufacturing.module.delivery.repository.DeliveryLineRepository;
import com.erp.manufacturing.module.delivery.repository.DeliveryRepository;
import com.erp.manufacturing.module.inventory.service.InventoryLotLookupService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.service.CommercialIdempotency;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.ProductionOutputLotLookupService;
import jakarta.persistence.EntityManager;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * G10b (FE Q26/Q28): creating, editing and cancelling need {@code PERM_DELIVERY_MANAGE}; posting needs
 * {@code PERM_DELIVERY_POST} — not MANAGE; reading accepts any of the three.
 */
@SpringJUnitConfig(DeliveryMethodSecurityTest.Config.class)
class DeliveryMethodSecurityTest {

    @Autowired DeliveryService service;
    @Autowired DeliveryQueryService queryService;
    @Autowired DeliveryPermissionGuard guard;
    @Autowired CommercialIdempotency idempotency;

    private final UUID deliveryId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(guard, idempotency);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_deniedWithoutManageOnTheOrdersPlant() {
        when(guard.canManageOrder(any(), eq(orderId))).thenReturn(false);
        assertThatThrownBy(() -> service.create(new DeliveryCreateRequest(orderId, 0L, LocalDate.now(), null,
                List.of(new DeliveryLineRequest(UUID.randomUUID(), "k", BigDecimal.ONE))), "key"))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).canManageOrder(any(), eq(orderId));
        verifyNoInteractions(idempotency);
    }

    @Test
    void post_deniedWithoutPostEvenWithManage() {
        when(guard.hasDeliveryAccess(any(), eq("PERM_DELIVERY_MANAGE"), eq(deliveryId))).thenReturn(true);
        when(guard.hasDeliveryAccess(any(), eq("PERM_DELIVERY_POST"), eq(deliveryId))).thenReturn(false);
        assertThatThrownBy(() -> service.post(deliveryId, new DeliveryPostRequest(0L), "key"))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).hasDeliveryAccess(any(), eq("PERM_DELIVERY_POST"), eq(deliveryId));
        verifyNoInteractions(idempotency);
    }

    @Test
    void cancel_deniedWithoutManage() {
        when(guard.hasDeliveryAccess(any(), eq("PERM_DELIVERY_MANAGE"), eq(deliveryId))).thenReturn(false);
        assertThatThrownBy(() -> service.cancel(deliveryId, new DeliveryCancelRequest(0L, "reason here"), "key"))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).hasDeliveryAccess(any(), eq("PERM_DELIVERY_MANAGE"), eq(deliveryId));
    }

    @Test
    void post_allowedWithPost_reachesTheService() {
        when(guard.hasDeliveryAccess(any(), eq("PERM_DELIVERY_POST"), eq(deliveryId))).thenReturn(true);
        assertThatThrownBy(() -> service.post(deliveryId, new DeliveryPostRequest(0L), "key"))
                .isNotInstanceOf(AccessDeniedException.class);
        verify(idempotency).key("key");
    }

    @Test
    void reads_deniedWithoutAnyDeliveryAuthority() {
        UUID plantId = UUID.randomUUID();
        when(guard.canReadDelivery(any(), eq(deliveryId))).thenReturn(false);
        when(guard.canReadPlant(any(), eq(plantId))).thenReturn(false);
        when(guard.canReadOrder(any(), eq(orderId))).thenReturn(false);
        assertThatThrownBy(() -> queryService.get(deliveryId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queryService.list(plantId, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queryService.candidates(orderId, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queryService.sources(orderId, UUID.randomUUID(), null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).canReadPlant(any(), eq(plantId));
    }

    @Test
    void readGuard_acceptsAnyOfTheThreePermissions_andNothingElse() {
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        UUID plantId = UUID.randomUUID();
        DeliveryPermissionGuard real = new DeliveryPermissionGuard(permissionGuard, mock(DeliveryRepository.class),
                mock(SalesOrderCommercialLookupService.class));
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_DELIVERY_POST"), eq("PLANT"), eq(plantId))).thenReturn(true);
        org.assertj.core.api.Assertions.assertThat(real.canReadPlant(null, plantId)).isTrue();
        reset(permissionGuard);
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_SALES_ORDER_READ"), eq("PLANT"), eq(plantId))).thenReturn(true);
        org.assertj.core.api.Assertions.assertThat(real.canReadPlant(null, plantId)).isFalse();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean(name = "deliveryPermissionGuard")
        DeliveryPermissionGuard deliveryPermissionGuard() { return mock(DeliveryPermissionGuard.class); }

        @Bean CommercialIdempotency commercialIdempotency() { return mock(CommercialIdempotency.class); }

        @Bean
        DeliveryService deliveryService(CommercialIdempotency idempotency) {
            DeliverySupport support = mock(DeliverySupport.class);
            return new DeliveryService(mock(DeliveryRepository.class), mock(DeliveryLineRepository.class), support,
                    mock(SalesOrderCommercialLookupService.class), mock(SalesOrderStockReservationService.class),
                    mock(InventoryMovementService.class), idempotency, mock(SecurityAuditorAware.class),
                    mock(EntityManager.class));
        }

        @Bean
        DeliveryQueryService deliveryQueryService() {
            return new DeliveryQueryService(mock(DeliveryRepository.class), mock(DeliveryLineRepository.class),
                    mock(DeliverySupport.class), mock(SalesOrderCommercialLookupService.class),
                    mock(SalesOrderStockReservationService.class), mock(InventoryMovementService.class),
                    mock(InventoryLotLookupService.class), mock(OrganizationLookupService.class),
                    mock(UserLookupService.class), mock(PermissionGuard.class), mock(SecurityAuditorAware.class),
                    mock(ProductionOutputLotLookupService.class));
        }
    }
}
