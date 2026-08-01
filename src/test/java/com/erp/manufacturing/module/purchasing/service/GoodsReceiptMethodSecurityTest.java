package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(GoodsReceiptMethodSecurityTest.Config.class)
@DisplayName("GoodsReceiptService method security")
class GoodsReceiptMethodSecurityTest {

    @Autowired GoodsReceiptService goodsReceiptService;
    @Autowired PurchasingPermissionGuard purchasingPermissionGuard;
    @Autowired GoodsReceiptRepository goodsReceiptRepository;
    @Autowired InventoryMovementService inventoryMovementService;

    @BeforeEach
    void setUp() {
        reset(purchasingPermissionGuard, goodsReceiptRepository, inventoryMovementService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void post_deniedWhenGoodsReceiptPostMissing() {
        UUID purchaseOrderId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasOrderAccess(any(), eq("PERM_GOODS_RECEIPT_POST"), eq(purchaseOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> goodsReceiptService.post(purchaseOrderId, null, "GR-KEY"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(goodsReceiptRepository, inventoryMovementService);
        verify(purchasingPermissionGuard).hasOrderAccess(any(), eq("PERM_GOODS_RECEIPT_POST"), eq(purchaseOrderId));
    }

    @Test
    void get_deniedWhenPurchaseOrderReadMissing() {
        UUID goodsReceiptId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasReceiptAccess(any(), eq("PERM_PURCHASE_ORDER_READ"), eq(goodsReceiptId)))
                .thenReturn(false);

        assertThatThrownBy(() -> goodsReceiptService.get(goodsReceiptId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(goodsReceiptRepository);
        verify(purchasingPermissionGuard).hasReceiptAccess(any(), eq("PERM_PURCHASE_ORDER_READ"), eq(goodsReceiptId));
    }

    @Test
    void cancel_deniedWhenPurchaseOrderManageMissing() {
        UUID goodsReceiptId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasReceiptAccess(any(), eq("PERM_PURCHASE_ORDER_MANAGE"), eq(goodsReceiptId)))
                .thenReturn(false);

        assertThatThrownBy(() -> goodsReceiptService.cancel(goodsReceiptId, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(goodsReceiptRepository, inventoryMovementService);
        verify(purchasingPermissionGuard).hasReceiptAccess(any(), eq("PERM_PURCHASE_ORDER_MANAGE"), eq(goodsReceiptId));
    }

    @Test
    void list_allowedWhenPurchaseOrderReadPresent() {
        UUID purchaseOrderId = UUID.randomUUID();
        when(purchasingPermissionGuard.hasOrderAccess(any(), eq("PERM_PURCHASE_ORDER_READ"), eq(purchaseOrderId)))
                .thenReturn(true);
        when(goodsReceiptRepository.findByPurchaseOrderPurchaseOrderId(eq(purchaseOrderId), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> goodsReceiptService.list(purchaseOrderId, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        GoodsReceiptService goodsReceiptService(GoodsReceiptRepository goodsReceiptRepository,
                                                PurchaseOrderRepository purchaseOrderRepository,
                                                InventoryMovementService inventoryMovementService,
                                                PurchasingMapper mapper) {
            return new GoodsReceiptService(
                    goodsReceiptRepository, purchaseOrderRepository, inventoryMovementService, mapper,
                    new IdempotencySupport(new ObjectMapper()));
        }

        @Bean PurchasingMapper purchasingMapper() { return new PurchasingMapper(); }

        @Bean(name = "purchasingPermissionGuard")
        PurchasingPermissionGuard purchasingPermissionGuard() { return mock(PurchasingPermissionGuard.class); }

        @Bean GoodsReceiptRepository goodsReceiptRepository() { return mock(GoodsReceiptRepository.class); }
        @Bean PurchaseOrderRepository purchaseOrderRepository() { return mock(PurchaseOrderRepository.class); }
        @Bean InventoryMovementService inventoryMovementService() { return mock(InventoryMovementService.class); }
    }
}
