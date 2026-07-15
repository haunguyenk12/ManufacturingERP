package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.*;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderVarianceService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ManufacturingExecutionMethodSecurityTest.Config.class)
@DisplayName("Manufacturing execution method security")
class ManufacturingExecutionMethodSecurityTest {

    @Autowired MaterialReservationService reservationService;
    @Autowired MaterialIssueService materialIssueService;
    @Autowired ProductionReceiptService receiptService;
    @Autowired WipTransactionService wipTransactionService;
    @Autowired WorkOrderVarianceService varianceService;
    @Autowired WorkOrderPermissionGuard workOrderPermissionGuard;
    @Autowired WorkOrderExecutionSupport support;

    @BeforeEach
    void setUp() {
        reset(workOrderPermissionGuard, support);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reservation_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_RESERVATION_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> reservationService.reserve(workOrderId, new MaterialReservationCreateRequest(
                UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.ONE)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
    }

    @Test
    void materialIssue_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> materialIssueService.post(workOrderId, new MaterialIssuePostRequest(null, List.of(
                new MaterialIssueLineRequest(UUID.randomUUID(), null, UUID.randomUUID(), null, null, BigDecimal.ONE, null))),
                "KEY-1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
    }

    @Test
    void productionReceipt_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.post(workOrderId, new ProductionReceiptPostRequest(null, List.of(
                new ProductionReceiptLineRequest(UUID.randomUUID(), null, null, BigDecimal.ONE, null))),
                "KEY-2"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
    }

    @Test
    void wip_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WIP_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> wipTransactionService.record(workOrderId, new WipTransactionRequest(
                WipTransactionType.SCRAP_REPORTED, null, BigDecimal.ONE, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
    }

    @Test
    void variance_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_VARIANCE_READ"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> varianceService.getVariance(workOrderId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        MaterialReservationService materialReservationService(MaterialReservationRepository reservationRepository,
                                                             StockBalanceRepository stockBalanceRepository,
                                                             WorkOrderExecutionSupport support,
                                                             ManufacturingExecutionMapper mapper) {
            return new MaterialReservationService(reservationRepository, stockBalanceRepository, support, mapper);
        }

        @Bean
        MaterialIssueService materialIssueService(MaterialIssueRepository issueRepository,
                                                  MaterialIssueLineRepository issueLineRepository,
                                                  MaterialReservationService reservationService,
                                                  InventoryMovementService movementService,
                                                  WipTransactionService wipTransactionService,
                                                  WorkOrderExecutionSupport support,
                                                  ManufacturingExecutionMapper mapper) {
            return new MaterialIssueService(
                    issueRepository, issueLineRepository, reservationService, movementService,
                    wipTransactionService, support, mapper);
        }

        @Bean
        ProductionReceiptService productionReceiptService(ProductionReceiptRepository receiptRepository,
                                                          ProductionReceiptLineRepository receiptLineRepository,
                                                          InventoryMovementService movementService,
                                                          WipTransactionService wipTransactionService,
                                                          WorkOrderExecutionSupport support,
                                                          ManufacturingExecutionMapper mapper) {
            return new ProductionReceiptService(
                    receiptRepository, receiptLineRepository, movementService,
                    wipTransactionService, support, mapper);
        }

        @Bean
        WipTransactionService wipTransactionService(WipTransactionRepository wipTransactionRepository,
                                                    WorkOrderExecutionSupport support,
                                                    ManufacturingExecutionMapper mapper) {
            return new WipTransactionService(wipTransactionRepository, support, mapper);
        }

        @Bean
        WorkOrderVarianceService workOrderVarianceService(MaterialIssueLineRepository issueLineRepository,
                                                          ProductionReceiptLineRepository receiptLineRepository,
                                                          WipTransactionRepository wipTransactionRepository,
                                                          WorkOrderRepository workOrderRepository) {
            return new WorkOrderVarianceService(
                    issueLineRepository, receiptLineRepository, wipTransactionRepository, workOrderRepository);
        }

        @Bean
        ManufacturingExecutionMapper manufacturingExecutionMapper() {
            return new ManufacturingExecutionMapper();
        }

        @Bean(name = "workOrderPermissionGuard")
        WorkOrderPermissionGuard workOrderPermissionGuard() {
            return mock(WorkOrderPermissionGuard.class);
        }

        @Bean
        WorkOrderExecutionSupport workOrderExecutionSupport() {
            return mock(WorkOrderExecutionSupport.class);
        }

        @Bean MaterialReservationRepository materialReservationRepository() { return mock(MaterialReservationRepository.class); }
        @Bean StockBalanceRepository stockBalanceRepository() { return mock(StockBalanceRepository.class); }
        @Bean MaterialIssueRepository materialIssueRepository() { return mock(MaterialIssueRepository.class); }
        @Bean MaterialIssueLineRepository materialIssueLineRepository() { return mock(MaterialIssueLineRepository.class); }
        @Bean InventoryMovementService inventoryMovementService() { return mock(InventoryMovementService.class); }
        @Bean ProductionReceiptRepository productionReceiptRepository() { return mock(ProductionReceiptRepository.class); }
        @Bean ProductionReceiptLineRepository productionReceiptLineRepository() { return mock(ProductionReceiptLineRepository.class); }
        @Bean WipTransactionRepository wipTransactionRepository() { return mock(WipTransactionRepository.class); }
        @Bean WorkOrderRepository workOrderRepository() { return mock(WorkOrderRepository.class); }
    }
}
