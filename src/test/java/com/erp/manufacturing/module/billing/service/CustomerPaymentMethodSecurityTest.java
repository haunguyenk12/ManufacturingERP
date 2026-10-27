package com.erp.manufacturing.module.billing.service;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.module.billing.domain.PaymentMethod;
import com.erp.manufacturing.module.billing.domain.SalesInvoice;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentAllocationRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentCancelRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentCreateRequest;
import com.erp.manufacturing.module.billing.dto.CustomerPaymentPostRequest;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentAllocationRepository;
import com.erp.manufacturing.module.billing.repository.CustomerPaymentRepository;
import com.erp.manufacturing.module.billing.repository.SalesInvoiceRepository;
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
 * G10d (FE Q26/Q28/Q30): creating, editing and cancelling need {@code PERM_CUSTOMER_PAYMENT_MANAGE} (on the plant of
 * every invoice allocated); posting needs {@code PERM_CUSTOMER_PAYMENT_POST} — not MANAGE; reading accepts any of the
 * three; an invoice's payments need invoice AND payment authority.
 */
@SpringJUnitConfig(CustomerPaymentMethodSecurityTest.Config.class)
class CustomerPaymentMethodSecurityTest {

    @Autowired CustomerPaymentService service;
    @Autowired CustomerPaymentQueryService queryService;
    @Autowired CustomerPaymentPermissionGuard guard;
    @Autowired SalesInvoicePermissionGuard invoiceGuard;
    @Autowired CommercialIdempotency idempotency;

    private final UUID paymentId = UUID.randomUUID();
    private final List<CustomerPaymentAllocationRequest> allocations =
            List.of(new CustomerPaymentAllocationRequest(UUID.randomUUID(), 1L, BigDecimal.TEN));

    @BeforeEach
    void setUp() {
        reset(guard, invoiceGuard, idempotency);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_deniedWithoutManageOnTheInvoicesPlant() {
        when(guard.canManageAllocations(any(), eq(allocations))).thenReturn(false);
        assertThatThrownBy(() -> service.create(new CustomerPaymentCreateRequest(UUID.randomUUID(), LocalDate.now(),
                PaymentMethod.CASH, null, null, BigDecimal.TEN, allocations), "key"))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).canManageAllocations(any(), eq(allocations));
        verifyNoInteractions(idempotency);
    }

    @Test
    void post_deniedWithoutPostEvenWithManage() {
        when(guard.hasPaymentAccess(any(), eq("PERM_CUSTOMER_PAYMENT_MANAGE"), eq(paymentId))).thenReturn(true);
        when(guard.hasPaymentAccess(any(), eq("PERM_CUSTOMER_PAYMENT_POST"), eq(paymentId))).thenReturn(false);
        assertThatThrownBy(() -> service.post(paymentId, new CustomerPaymentPostRequest(0L), "key"))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).hasPaymentAccess(any(), eq("PERM_CUSTOMER_PAYMENT_POST"), eq(paymentId));
        verifyNoInteractions(idempotency);
    }

    @Test
    void cancel_deniedWithoutManage_andPostAllowedWithPost() {
        when(guard.hasPaymentAccess(any(), eq("PERM_CUSTOMER_PAYMENT_MANAGE"), eq(paymentId))).thenReturn(false);
        assertThatThrownBy(() -> service.cancel(paymentId, new CustomerPaymentCancelRequest(0L, "reason here"), "key"))
                .isInstanceOf(AccessDeniedException.class);
        when(guard.hasPaymentAccess(any(), eq("PERM_CUSTOMER_PAYMENT_POST"), eq(paymentId))).thenReturn(true);
        assertThatThrownBy(() -> service.post(paymentId, new CustomerPaymentPostRequest(0L), "key"))
                .isNotInstanceOf(AccessDeniedException.class);
        verify(idempotency).key("key");
    }

    @Test
    void invoicePayments_needInvoiceAndPaymentAuthority() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceGuard.canReadInvoice(any(), eq(invoiceId))).thenReturn(true);
        when(guard.canReadInvoicePayments(any(), eq(invoiceId))).thenReturn(false);
        assertThatThrownBy(() -> queryService.invoicePayments(invoiceId, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard).canReadInvoicePayments(any(), eq(invoiceId));

        when(invoiceGuard.canReadInvoice(any(), eq(invoiceId))).thenReturn(false);
        when(guard.canReadInvoicePayments(any(), eq(invoiceId))).thenReturn(true);
        assertThatThrownBy(() -> queryService.invoicePayments(invoiceId, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void reads_deniedWithoutAnyPaymentAuthority() {
        UUID plantId = UUID.randomUUID();
        when(guard.canReadPayment(any(), eq(paymentId))).thenReturn(false);
        when(guard.canReadPlant(any(), eq(plantId))).thenReturn(false);
        assertThatThrownBy(() -> queryService.get(paymentId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queryService.list(plantId, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queryService.invoiceCandidates(plantId, UUID.randomUUID(), null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verify(guard, times(2)).canReadPlant(any(), eq(plantId));
    }

    @Test
    void realGuard_readAcceptsAnyOfTheThree_andManageNeedsEveryInvoicesPlant() {
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        SalesInvoiceRepository invoices = mock(SalesInvoiceRepository.class);
        CustomerPaymentPermissionGuard real = new CustomerPaymentPermissionGuard(permissionGuard,
                mock(CustomerPaymentRepository.class), invoices);
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_CUSTOMER_PAYMENT_POST"), eq("PLANT"), eq(plantId))).thenReturn(true);
        assertThat(real.canReadPlant(null, plantId)).isTrue();
        reset(permissionGuard);
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_SALES_INVOICE_READ"), eq("PLANT"), eq(plantId))).thenReturn(true);
        assertThat(real.canReadPlant(null, plantId)).as("invoice authority is not payment authority").isFalse();

        UUID other = UUID.randomUUID();
        SalesInvoice a = SalesInvoice.builder().invoiceId(UUID.randomUUID()).plantId(plantId).build();
        SalesInvoice b = SalesInvoice.builder().invoiceId(UUID.randomUUID()).plantId(other).build();
        List<CustomerPaymentAllocationRequest> two = List.of(
                new CustomerPaymentAllocationRequest(a.getInvoiceId(), 1L, BigDecimal.ONE),
                new CustomerPaymentAllocationRequest(b.getInvoiceId(), 1L, BigDecimal.ONE));
        when(invoices.findAllById(any())).thenReturn(List.of(a, b));
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_CUSTOMER_PAYMENT_MANAGE"), eq("PLANT"), eq(plantId))).thenReturn(true);
        assertThat(real.canManageAllocations(null, two)).as("MANAGE on one plant only").isFalse();
        when(invoices.findAllById(any())).thenReturn(List.of(a));
        assertThat(real.canManageAllocations(null, two)).as("an unknown invoice is no access").isFalse();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean(name = "customerPaymentPermissionGuard")
        CustomerPaymentPermissionGuard customerPaymentPermissionGuard() { return mock(CustomerPaymentPermissionGuard.class); }

        @Bean(name = "salesInvoicePermissionGuard")
        SalesInvoicePermissionGuard salesInvoicePermissionGuard() { return mock(SalesInvoicePermissionGuard.class); }

        @Bean CommercialIdempotency commercialIdempotency() { return mock(CommercialIdempotency.class); }

        @Bean
        CustomerPaymentService customerPaymentService(CommercialIdempotency idempotency) {
            return new CustomerPaymentService(mock(CustomerPaymentRepository.class),
                    mock(CustomerPaymentAllocationRepository.class), mock(SalesInvoiceRepository.class),
                    mock(CustomerPaymentSupport.class), idempotency, mock(SecurityAuditorAware.class),
                    mock(EntityManager.class));
        }

        @Bean
        CustomerPaymentQueryService customerPaymentQueryService() {
            return new CustomerPaymentQueryService(mock(CustomerPaymentRepository.class),
                    mock(CustomerPaymentAllocationRepository.class), mock(SalesInvoiceRepository.class),
                    mock(CustomerPaymentSupport.class), mock(SalesOrderCommercialLookupService.class),
                    mock(UserLookupService.class), mock(PermissionGuard.class), mock(SecurityAuditorAware.class));
        }
    }
}
