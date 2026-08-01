package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.workorder.domain.QualityDispositionResult;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.*;
import com.erp.manufacturing.module.workorder.service.WorkOrderDemandAllocationService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderVarianceService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @Autowired PermissionGuard permissionGuard;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired WorkOrderExecutionSupport support;
    @Autowired MaterialReservationRepository reservationRepository;
    @Autowired ProductionReceiptRepository productionReceiptRepository;

    @BeforeEach
    void setUp() {
        reset(workOrderPermissionGuard, permissionGuard, support, reservationRepository,
                productionReceiptRepository, workOrderRepository);
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
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_MATERIAL_RESERVATION_MANAGE"), eq(workOrderId));
    }

    @Test
    void listReservations_allowedWhenPermissionPresent() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_RESERVATION_MANAGE"), eq(workOrderId)))
                .thenReturn(true);
        when(reservationRepository.findByWorkOrderWorkOrderId(eq(workOrderId), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> reservationService.list(workOrderId, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void materialIssue_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> materialIssueService.post(workOrderId, new MaterialIssuePostRequest(null, List.of(
                new MaterialIssueLineRequest(UUID.randomUUID(), null, UUID.randomUUID(), null, null, BigDecimal.ONE, null, null))),
                "KEY-1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq(workOrderId));
    }

    @Test
    void productionReceipt_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.post(workOrderId, new ProductionReceiptPostRequest(
                UUID.randomUUID(), null, null, BigDecimal.ONE, null, null),
                "KEY-2"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId));
    }

    @Test
    void submitProductionReceipt_deniedWhenManagePermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.submit(workOrderId, receiptId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId));
    }

    @Test
    void qcDisposition_deniedWhenQualityPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_QUALITY_DISPOSITION"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.qcDisposition(workOrderId, receiptId,
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, "Passed")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_QUALITY_DISPOSITION"), eq(workOrderId));
    }

    @Test
    void qcDisposition_allowedWhenQualityPermissionPresent() {
        // Allow branch: proves PERM_QUALITY_DISPOSITION is the string actually checked. Without it
        // a typo'd permission would still deny and the deny test above would stay green (rule R2).
        UUID workOrderId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_QUALITY_DISPOSITION"), eq(workOrderId)))
                .thenReturn(true);
        when(productionReceiptRepository.findWithLinesByReceiptId(receiptId)).thenReturn(Optional.empty());

        // Passes method security, then fails on the missing receipt — not on authorization.
        assertThatThrownBy(() -> receiptService.qcDisposition(workOrderId, receiptId,
                new ProductionReceiptQcDispositionRequest(QualityDispositionResult.AVAILABLE, "Passed")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void approveProductionReceipt_deniedWhenApprovePermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_APPROVE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.approve(workOrderId, receiptId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_APPROVE"), eq(workOrderId));
    }

    @Test
    void rejectProductionReceipt_deniedWhenApprovePermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_APPROVE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.reject(workOrderId, receiptId,
                new ProductionReceiptRejectRequest("Quality failed")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_APPROVE"), eq(workOrderId));
    }

    @Test
    void wip_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WIP_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> wipTransactionService.record(workOrderId, new WipTransactionRequest(
                WipTransactionType.SCRAP_REPORTED, null, null, BigDecimal.ONE, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_WIP_MANAGE"), eq(workOrderId));
    }

    @Test
    void variance_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_VARIANCE_READ"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> varianceService.getVariance(workOrderId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_WORK_ORDER_VARIANCE_READ"), eq(workOrderId));
    }

    // ---------------------------------------------------------------------------------------
    // F8 — the plant-scoped read endpoints. These authorise through `permissionGuard`, not
    // `workOrderPermissionGuard`: there is no work order id yet on a candidate/list screen.
    // ---------------------------------------------------------------------------------------

    @Test
    void receiptCandidates_deniedWhenPlantScopedPermissionMissing() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> receiptService.listCandidates(plantId, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderRepository);
        // Pins the permission string itself: without this the mock would return false for any
        // name and the test would stay green after a typo (R2).
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq("PLANT"), eq(plantId));
    }

    @Test
    void receiptCandidates_allowedWhenPlantScopedPermissionPresent() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq("PLANT"), eq(plantId))).thenReturn(true);
        when(workOrderRepository.findReceiptCandidates(eq(plantId), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> receiptService.listCandidates(plantId, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void receiptListByPlant_deniedWhenPlantScopedPermissionMissing() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> receiptService.listByPlant(plantId, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productionReceiptRepository);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq("PLANT"), eq(plantId));
    }

    @Test
    void materialIssueListByPlant_deniedWhenPlantScopedPermissionMissing() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> materialIssueService.listByPlant(plantId, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq("PLANT"), eq(plantId));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        MaterialReservationService materialReservationService(MaterialReservationRepository reservationRepository,
                                                             StockBalanceRepository stockBalanceRepository,
                                                             OrganizationLookupService organizationLookupService,
                                                             WorkOrderExecutionSupport support,
                                                             ManufacturingExecutionMapper mapper) {
            return new MaterialReservationService(
                    reservationRepository, stockBalanceRepository, organizationLookupService, support, mapper);
        }

        @Bean
        MaterialIssueService materialIssueService(MaterialIssueRepository issueRepository,
                                                  MaterialIssueLineRepository issueLineRepository,
                                                  MaterialReservationService reservationService,
                                                  InventoryMovementService movementService,
                                                  WipTransactionService wipTransactionService,
                                                  WorkOrderPermissionGuard workOrderPermissionGuard,
                                                  WorkOrderExecutionSupport support,
                                                  ManufacturingExecutionMapper mapper,
                                                  UserLookupService userLookupService) {
            return new MaterialIssueService(
                    issueRepository, issueLineRepository, reservationService, movementService,
                    wipTransactionService, workOrderPermissionGuard, support,
                    new IdempotencySupport(new ObjectMapper()), mapper, new TraceIdProvider(),
                    userLookupService);
        }

        @Bean
        ProductionReceiptService productionReceiptService(ProductionReceiptRepository receiptRepository,
                                                          WorkOrderRepository workOrderRepository,
                                                          ProductionReceiptLineRepository receiptLineRepository,
                                                          ProductionExecutionRepository executionRepository,
                                                          QualityDispositionRepository dispositionRepository,
                                                          InventoryMovementService movementService,
                                                          WipTransactionService wipTransactionService,
                                                          ItemLookupService itemLookupService,
                                                          UserLookupService userLookupService,
                                                          SecurityAuditorAware auditorAware,
                                                          WorkOrderDemandAllocationService allocationService,
                                                          WorkOrderExecutionSupport support,
                                                          ManufacturingExecutionMapper mapper) {
            return new ProductionReceiptService(
                    receiptRepository, workOrderRepository, receiptLineRepository, executionRepository,
                    dispositionRepository, movementService,
                    wipTransactionService, itemLookupService, userLookupService, auditorAware, allocationService, support,
                    mapper, new IdempotencySupport(new ObjectMapper()), new TraceIdProvider());
        }

        @Bean QualityDispositionRepository qualityDispositionRepository() { return mock(QualityDispositionRepository.class); }

        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
        @Bean ProductionExecutionRepository productionExecutionRepository() { return mock(ProductionExecutionRepository.class); }
        @Bean UserLookupService userLookupService() { return mock(UserLookupService.class); }
        @Bean WorkOrderOperationRepository workOrderOperationRepository() { return mock(WorkOrderOperationRepository.class); }

        @Bean WorkOrderDemandAllocationService workOrderDemandAllocationService() { return mock(WorkOrderDemandAllocationService.class); }

        @Bean ItemLookupService itemLookupService() { return mock(ItemLookupService.class); }
        @Bean SecurityAuditorAware securityAuditorAware() { return mock(SecurityAuditorAware.class); }

        @Bean
        WipTransactionService wipTransactionService(WipTransactionRepository wipTransactionRepository,
                                                    WorkOrderOperationRepository operationRepository,
                                                    WorkOrderExecutionSupport support,
                                                    ManufacturingExecutionMapper mapper) {
            return new WipTransactionService(wipTransactionRepository, operationRepository, support, mapper);
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

        /**
         * The plant-scoped guard, needed since F7/F8 added endpoints that authorise on a plant rather
         * than on one work order — {@code hasWorkOrderAccess} has no id to take before the work order
         * has been chosen.
         */
        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
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
