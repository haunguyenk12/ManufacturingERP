package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCancelRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceCreateRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoiceLineRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePostRequest;
import com.erp.manufacturing.module.billing.dto.SalesInvoicePreviewRequest;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceLineRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.sales.service.CommercialIdempotency;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * G10c (FE Q26/Q28): previewing, creating, editing and cancelling need {@code PERM_SALES_INVOICE_MANAGE}; posting
 * needs {@code PERM_SALES_INVOICE_POST} — not MANAGE; reading accepts any of the three, and nothing else.
 */
@SpringJUnitConfig(SalesInvoiceMethodSecurityTest.Config.class)
class SalesInvoiceMethodSecurityTest {

    @Autowired SalesInvoiceService service;
    @Autowired SalesInvoiceQueryService queryService;
    @Autowired SalesInvoicePermissionGuard guard;
    @Autowired CommercialIdempotency idempotency;

    private final UUID invoiceId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final List<SalesInvoiceLineRequest> lines = List.of(new SalesInvoiceLineRequest(UUID.randomUUID(), BigDecimal.ONE));

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
    void previewAndCreate_deniedWithoutManageOnTheOrdersPlant() {
        when(guard.canManageOrder(any(), eq(orderId))).thenReturn(false);
        assertThatThrownBy(() -> service.preview(new SalesInvoicePreviewRequest(orderId, 0L, null, lines)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(new SalesInvoiceCreateRequest(orderId, 0L, "BILL-x", LocalDate.now(),
                LocalDate.now(), null, lines), "key"))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard, times(2)).canManageOrder(any(), eq(orderId));
        verifyNoInteractions(idempotency);
    }

    @Test
    void post_deniedWithoutPostEvenWithManage() {
        when(guard.hasInvoiceAccess(any(), eq("PERM_SALES_INVOICE_MANAGE"), eq(invoiceId))).thenReturn(true);
        when(guard.hasInvoiceAccess(any(), eq("PERM_SALES_INVOICE_POST"), eq(invoiceId))).thenReturn(false);
        assertThatThrownBy(() -> service.post(invoiceId, new SalesInvoicePostRequest(0L), "key"))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).hasInvoiceAccess(any(), eq("PERM_SALES_INVOICE_POST"), eq(invoiceId));
        verifyNoInteractions(idempotency);
    }

    @Test
    void cancel_deniedWithoutManage() {
        when(guard.hasInvoiceAccess(any(), eq("PERM_SALES_INVOICE_MANAGE"), eq(invoiceId))).thenReturn(false);
        assertThatThrownBy(() -> service.cancel(invoiceId, new SalesInvoiceCancelRequest(0L, "reason here"), "key"))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).hasInvoiceAccess(any(), eq("PERM_SALES_INVOICE_MANAGE"), eq(invoiceId));
    }

    @Test
    void post_allowedWithPost_reachesTheService() {
        when(guard.hasInvoiceAccess(any(), eq("PERM_SALES_INVOICE_POST"), eq(invoiceId))).thenReturn(true);
        assertThatThrownBy(() -> service.post(invoiceId, new SalesInvoicePostRequest(0L), "key"))
                .isNotInstanceOf(AccessDeniedException.class);
        verify(idempotency).key("key");
    }

    @Test
    void reads_deniedWithoutAnyInvoiceAuthority() {
        UUID plantId = UUID.randomUUID();
        when(guard.canReadInvoice(any(), eq(invoiceId))).thenReturn(false);
        when(guard.canReadPlant(any(), eq(plantId))).thenReturn(false);
        when(guard.canReadOrder(any(), eq(orderId))).thenReturn(false);
        assertThatThrownBy(() -> queryService.get(invoiceId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queryService.history(invoiceId, PageRequest.of(0, 20))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queryService.list(plantId, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queryService.candidates(orderId, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).canReadPlant(any(), eq(plantId));
        verify(guard).canReadOrder(any(), eq(orderId));
    }

    @Test
    void readGuard_acceptsAnyOfTheThreeInvoicePermissions_andNotADeliveryOne() {
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        UUID plantId = UUID.randomUUID();
        SalesInvoicePermissionGuard real = new SalesInvoicePermissionGuard(permissionGuard,
                mock(SalesInvoiceRepository.class), mock(SalesOrderCommercialLookupService.class));
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_SALES_INVOICE_POST"), eq("PLANT"), eq(plantId))).thenReturn(true);
        assertThat(real.canReadPlant(null, plantId)).isTrue();
        reset(permissionGuard);
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_DELIVERY_READ"), eq("PLANT"), eq(plantId))).thenReturn(true);
        assertThat(real.canReadPlant(null, plantId)).isFalse();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean(name = "salesInvoicePermissionGuard")
        SalesInvoicePermissionGuard salesInvoicePermissionGuard() { return mock(SalesInvoicePermissionGuard.class); }

        @Bean CommercialIdempotency commercialIdempotency() { return mock(CommercialIdempotency.class); }

        @Bean
        SalesInvoiceService salesInvoiceService(CommercialIdempotency idempotency) {
            return new SalesInvoiceService(mock(SalesInvoiceRepository.class), mock(SalesInvoiceLineRepository.class),
                    mock(SalesInvoiceSupport.class), mock(SalesOrderCommercialLookupService.class), idempotency,
                    mock(SecurityAuditorAware.class), mock(EntityManager.class));
        }

        @Bean
        SalesInvoiceQueryService salesInvoiceQueryService() {
            return new SalesInvoiceQueryService(mock(SalesInvoiceRepository.class), mock(SalesInvoiceLineRepository.class),
                    mock(SalesInvoiceSupport.class), mock(CustomerPaymentSupport.class), mock(DeliveryLookupService.class),
                    mock(SalesOrderCommercialLookupService.class), mock(UserLookupService.class),
                    mock(PermissionGuard.class), mock(SecurityAuditorAware.class));
        }
    }
}
