package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.purchasing.dto.PurchaseQcDecisionRequest;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptLineRepository;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseQualityDecisionRepository;
import com.erp.manufacturing.module.user.service.UserLookupService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
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

@SpringJUnitConfig(PurchaseQualityMethodSecurityTest.Config.class)
@DisplayName("PurchaseQualityService method security (G7c)")
class PurchaseQualityMethodSecurityTest {

    @Autowired PurchaseQualityService service;
    @Autowired PermissionGuard permissionGuard;
    @Autowired PurchasingPermissionGuard purchasingPermissionGuard;
    @Autowired GoodsReceiptRepository goodsReceiptRepository;
    @Autowired GoodsReceiptLineRepository goodsReceiptLineRepository;
    @Autowired PurchaseQualityDecisionRepository decisionRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, purchasingPermissionGuard, goodsReceiptRepository, goodsReceiptLineRepository, decisionRepository);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static PurchaseQcDecisionRequest request() {
        return new PurchaseQcDecisionRequest(1L, BigDecimal.ONE, BigDecimal.ZERO, null);
    }

    @Test
    void decide_deniedWithoutPurchaseQcManage() {
        UUID receiptId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasReceiptAccess(any(), eq("PERM_PURCHASE_QC_MANAGE"), eq(receiptId))).thenReturn(false);

        assertThatThrownBy(() -> service.decide(receiptId, UUID.randomUUID(), request(), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(goodsReceiptRepository, decisionRepository);
        verify(purchasingPermissionGuard).hasReceiptAccess(any(), eq("PERM_PURCHASE_QC_MANAGE"), eq(receiptId));
    }

    @Test
    void decide_allowedWithPurchaseQcManage_reachesTheLock() {
        UUID receiptId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasReceiptAccess(any(), eq("PERM_PURCHASE_QC_MANAGE"), eq(receiptId))).thenReturn(true);
        when(goodsReceiptRepository.lockRowByGoodsReceiptId(receiptId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.decide(receiptId, UUID.randomUUID(), request(), null))
                .isInstanceOf(com.erp.manufacturing.common.exception.AppException.class);
        verify(goodsReceiptRepository).lockRowByGoodsReceiptId(receiptId);
    }

    @Test
    void queue_deniedWithoutPurchaseQcReadOnThePlant() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_PURCHASE_QC_READ"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> service.queue(UUID.randomUUID(), plantId, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(goodsReceiptLineRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_PURCHASE_QC_READ"), eq("PLANT"), eq(plantId));
    }

    @Test
    void decisions_allowedWithGoodsReceiptReadAlone() {
        UUID receiptId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasReceiptAccess(any(), eq("PERM_GOODS_RECEIPT_READ"), eq(receiptId))).thenReturn(true);
        when(decisionRepository.findByReceipt(eq(receiptId), any())).thenReturn(Page.empty());

        service.decisions(receiptId, PageRequest.of(0, 20));

        verify(decisionRepository).findByReceipt(eq(receiptId), any());
    }

    @Test
    void decisions_deniedWithoutAnyReadPermission() {
        UUID receiptId = UUID.randomUUID();

        assertThatThrownBy(() -> service.decisions(receiptId, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(decisionRepository);
        verify(purchasingPermissionGuard).hasReceiptAccess(any(), eq("PERM_PURCHASE_QC_READ"), eq(receiptId));
        verify(purchasingPermissionGuard).hasReceiptAccess(any(), eq("PERM_GOODS_RECEIPT_READ"), eq(receiptId));
        verify(purchasingPermissionGuard).hasReceiptAccess(any(), eq("PERM_PURCHASE_ORDER_READ"), eq(receiptId));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        PurchaseQualityService purchaseQualityService(GoodsReceiptRepository goodsReceiptRepository,
                                                      GoodsReceiptLineRepository goodsReceiptLineRepository,
                                                      PurchaseQualityDecisionRepository decisionRepository) {
            return new PurchaseQualityService(goodsReceiptRepository, goodsReceiptLineRepository, decisionRepository,
                    mock(InventoryMovementService.class), mock(ItemLookupService.class), mock(PurchasingIdempotency.class),
                    mock(UserLookupService.class), mock(AuditorAware.class), mock(EntityManager.class));
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "purchasingPermissionGuard")
        PurchasingPermissionGuard purchasingPermissionGuard() { return mock(PurchasingPermissionGuard.class); }

        @Bean GoodsReceiptRepository goodsReceiptRepository() { return mock(GoodsReceiptRepository.class); }
        @Bean GoodsReceiptLineRepository goodsReceiptLineRepository() { return mock(GoodsReceiptLineRepository.class); }
        @Bean PurchaseQualityDecisionRepository decisionRepository() { return mock(PurchaseQualityDecisionRepository.class); }
    }
}
