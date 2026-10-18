package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptDraftCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptDraftLineRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptUpdateRequest;
import com.erp.manufacturing.module.purchasing.dto.GoodsReceiptVersionRequest;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderLineRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(GoodsReceiptDraftMethodSecurityTest.Config.class)
@DisplayName("GoodsReceiptDraftService method security (G7b)")
class GoodsReceiptDraftMethodSecurityTest {

    @Autowired GoodsReceiptDraftService service;
    @Autowired PermissionGuard permissionGuard;
    @Autowired PurchasingPermissionGuard purchasingPermissionGuard;
    @Autowired GoodsReceiptRepository goodsReceiptRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired OrganizationLookupService organizationLookupService;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, purchasingPermissionGuard, goodsReceiptRepository, purchaseOrderRepository,
                organizationLookupService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static List<GoodsReceiptDraftLineRequest> oneLine() {
        return List.of(new GoodsReceiptDraftLineRequest(null, UUID.randomUUID(), BigDecimal.ONE, null, null));
    }

    @Test
    void createDraft_deniedWithoutGoodsReceiptManageOnTheOrder() {
        UUID orderId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasOrderAccess(any(), eq("PERM_GOODS_RECEIPT_MANAGE"), eq(orderId))).thenReturn(false);

        assertThatThrownBy(() -> service.createDraft(new GoodsReceiptDraftCreateRequest(orderId, LocalDate.now(), null, null,
                oneLine()), null)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(purchaseOrderRepository, goodsReceiptRepository);
        verify(purchasingPermissionGuard).hasOrderAccess(any(), eq("PERM_GOODS_RECEIPT_MANAGE"), eq(orderId));
    }

    @Test
    void createDraft_allowedWithGoodsReceiptManage_reachesTheService() {
        UUID orderId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasOrderAccess(any(), eq("PERM_GOODS_RECEIPT_MANAGE"), eq(orderId))).thenReturn(true);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraft(new GoodsReceiptDraftCreateRequest(orderId, LocalDate.now(), null, null,
                oneLine()), null)).isInstanceOf(AppException.class);
        verify(purchaseOrderRepository).findWithDetailsByPurchaseOrderId(orderId);
    }

    @Test
    void updateDraft_deniedWithoutGoodsReceiptManage() {
        UUID receiptId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasReceiptAccess(any(), eq("PERM_GOODS_RECEIPT_MANAGE"), eq(receiptId))).thenReturn(false);

        assertThatThrownBy(() -> service.updateDraft(receiptId, new GoodsReceiptUpdateRequest(1L, LocalDate.now(), null,
                null, oneLine()), null)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(goodsReceiptRepository);
        verify(purchasingPermissionGuard).hasReceiptAccess(any(), eq("PERM_GOODS_RECEIPT_MANAGE"), eq(receiptId));
    }

    @Test
    void post_deniedWithoutGoodsReceiptPost() {
        UUID receiptId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasReceiptAccess(any(), eq("PERM_GOODS_RECEIPT_POST"), eq(receiptId))).thenReturn(false);

        assertThatThrownBy(() -> service.post(receiptId, new GoodsReceiptVersionRequest(1L), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(goodsReceiptRepository, purchaseOrderRepository);
        verify(purchasingPermissionGuard).hasReceiptAccess(any(), eq("PERM_GOODS_RECEIPT_POST"), eq(receiptId));
    }

    @Test
    void list_deniedWithoutGoodsReceiptReadOnThePlant() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_GOODS_RECEIPT_READ"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> service.list(UUID.randomUUID(), plantId, null, null, null, null, null, null,
                PageRequest.of(0, 20))).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(goodsReceiptRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_GOODS_RECEIPT_READ"), eq("PLANT"), eq(plantId));
    }

    @Test
    void candidates_deniedWithoutGoodsReceiptReadOnThePlant() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_GOODS_RECEIPT_READ"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> service.candidates(UUID.randomUUID(), plantId, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(purchaseOrderRepository, organizationLookupService);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_GOODS_RECEIPT_READ"), eq("PLANT"), eq(plantId));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        GoodsReceiptDraftService goodsReceiptDraftService(GoodsReceiptRepository goodsReceiptRepository,
                                                          PurchaseOrderRepository purchaseOrderRepository,
                                                          OrganizationLookupService organizationLookupService) {
            return new GoodsReceiptDraftService(goodsReceiptRepository, purchaseOrderRepository,
                    mock(PurchaseOrderLineRepository.class), organizationLookupService, mock(GoodsReceiptPoster.class),
                    mock(PurchasingIdempotency.class), new PurchasingMapper(), mock(AuditorAware.class));
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "purchasingPermissionGuard")
        PurchasingPermissionGuard purchasingPermissionGuard() { return mock(PurchasingPermissionGuard.class); }

        @Bean GoodsReceiptRepository goodsReceiptRepository() { return mock(GoodsReceiptRepository.class); }
        @Bean PurchaseOrderRepository purchaseOrderRepository() { return mock(PurchaseOrderRepository.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
    }
}
