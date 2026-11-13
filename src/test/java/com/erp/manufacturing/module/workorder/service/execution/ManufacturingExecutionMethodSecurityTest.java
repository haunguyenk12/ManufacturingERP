package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.module.inventory.service.StockBalanceReservationService;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.repository.WorkOrderIdempotencyRecordRepository;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.costing.service.ItemStandardCostLookupService;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.service.InventoryLotLookupService;
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
    @Autowired MaterialIssueCandidateService candidateService;
    @Autowired ProductionReceiptService receiptService;
    @Autowired WipTransactionService wipTransactionService;
    @Autowired WorkOrderVarianceService varianceService;
    @Autowired ProductionExecutionService productionExecutionService;
    @Autowired OperationService operationService;
    @Autowired OperationLedger operationLedger;
    @Autowired WorkOrderPermissionGuard workOrderPermissionGuard;
    @Autowired PermissionGuard permissionGuard;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired WorkOrderExecutionSupport support;
    @Autowired MaterialReservationRepository reservationRepository;
    @Autowired ProductionReceiptRepository productionReceiptRepository;

    @BeforeEach
    void setUp() {
        reset(workOrderPermissionGuard, permissionGuard, support, reservationRepository,
                productionReceiptRepository, workOrderRepository, operationLedger);
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
                UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.ONE, 0L), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_MATERIAL_RESERVATION_MANAGE"), eq(workOrderId));
    }

    /** G8a release-reservations: the same permission as releasing one reservation. */
    @Test
    void releaseAllReservations_deniedWhenPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_RESERVATION_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> reservationService.releaseAll(workOrderId,
                new com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest(0L), null))
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
                new MaterialIssueLineRequest(UUID.randomUUID(), null, UUID.randomUUID(), null, null, null, BigDecimal.ONE, null, null))),
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
                UUID.randomUUID(), null, null, null, BigDecimal.ONE, null, null),
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

        assertThatThrownBy(() -> receiptService.submit(workOrderId, receiptId,
                new ProductionReceiptVersionRequest(0L, 0L), null))
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

        assertThatThrownBy(() -> receiptService.approve(workOrderId, receiptId,
                new ProductionReceiptVersionRequest(0L, 0L), null))
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
                new ProductionReceiptReasonRequest(0L, 0L, "Quality failed"), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_APPROVE"), eq(workOrderId));
    }

    // ---------------------------------------------------------------------------------------
    // G9a — receipt lifecycle and reads
    // ---------------------------------------------------------------------------------------

    private static final List<String> RECEIPT_READERS = List.of("PERM_PRODUCTION_RECEIPT_READ",
            "PERM_PRODUCTION_RECEIPT_MANAGE", "PERM_PRODUCTION_RECEIPT_APPROVE", "PERM_PRODUCTION_QC_READ",
            "PERM_QUALITY_DISPOSITION");

    @Test
    void updateProductionReceipt_deniedWhenManagePermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.update(workOrderId, UUID.randomUUID(),
                new ProductionReceiptUpdateRequest(0L, 0L, BigDecimal.ONE, null, java.time.LocalDate.now(), null, null),
                null)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support, productionReceiptRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId));
    }

    @Test
    void withdrawProductionReceipt_deniedWhenManagePermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.withdraw(workOrderId, UUID.randomUUID(),
                new ProductionReceiptReasonRequest(0L, 0L, "Wrong lot"), null)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support, productionReceiptRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId));
    }

    @Test
    void cancelProductionReceipt_deniedWhenManagePermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.cancel(workOrderId, UUID.randomUUID(),
                new ProductionReceiptReasonRequest(0L, 0L, "Entered twice"), null)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support, productionReceiptRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq(workOrderId));
    }

    /** D6: reading one receipt accepts the new READ permission, the action ones and the two QC ones. */
    @Test
    void getProductionReceipt_deniedWhenNoReceiptOrQualityPermissionIsHeld() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasAnyWorkOrderAccess(any(), eq(RECEIPT_READERS), eq(workOrderId))).thenReturn(false);

        assertThatThrownBy(() -> receiptService.get(workOrderId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productionReceiptRepository);
        verify(workOrderPermissionGuard).hasAnyWorkOrderAccess(any(), eq(RECEIPT_READERS), eq(workOrderId));
    }

    /** G9c: a QC decision needs PERM_QUALITY_DISPOSITION — reading receipts is not enough. */
    @Test
    void decideProductionQuality_deniedWhenQualityPermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_QUALITY_DISPOSITION"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> receiptService.decideQuality(workOrderId, UUID.randomUUID(),
                new com.erp.manufacturing.module.workorder.dto.execution.ProductionQcDecisionRequest(
                        0L, 0L, BigDecimal.ONE, BigDecimal.ZERO, null), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productionReceiptRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_QUALITY_DISPOSITION"), eq(workOrderId));
    }

    @Test
    void decideProductionQuality_allowedWhenQualityPermissionPresent() {
        UUID workOrderId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_QUALITY_DISPOSITION"), eq(workOrderId)))
                .thenReturn(true);
        when(productionReceiptRepository.findWithLinesByReceiptId(receiptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receiptService.decideQuality(workOrderId, receiptId,
                new com.erp.manufacturing.module.workorder.dto.execution.ProductionQcDecisionRequest(
                        0L, 0L, BigDecimal.ONE, BigDecimal.ZERO, null), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void productionQualityDecisions_deniedWhenNoReceiptOrQualityPermissionIsHeld() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasAnyWorkOrderAccess(any(), eq(RECEIPT_READERS), eq(workOrderId))).thenReturn(false);

        assertThatThrownBy(() -> receiptService.qualityDecisions(workOrderId, UUID.randomUUID(), PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productionReceiptRepository);
        verify(workOrderPermissionGuard).hasAnyWorkOrderAccess(any(), eq(RECEIPT_READERS), eq(workOrderId));
    }

    /** G9b: the source of a receipt is read with the same permissions as the receipt itself. */
    @Test
    void productionReceiptSource_deniedWhenNoReceiptOrQualityPermissionIsHeld() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasAnyWorkOrderAccess(any(), eq(RECEIPT_READERS), eq(workOrderId))).thenReturn(false);

        assertThatThrownBy(() -> receiptService.source(workOrderId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productionReceiptRepository);
        verify(workOrderPermissionGuard).hasAnyWorkOrderAccess(any(), eq(RECEIPT_READERS), eq(workOrderId));
    }

    @Test
    void productionReceiptEvents_deniedWhenNoReceiptOrQualityPermissionIsHeld() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasAnyWorkOrderAccess(any(), eq(RECEIPT_READERS), eq(workOrderId))).thenReturn(false);

        assertThatThrownBy(() -> receiptService.events(workOrderId, UUID.randomUUID(), PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productionReceiptRepository);
        verify(workOrderPermissionGuard).hasAnyWorkOrderAccess(any(), eq(RECEIPT_READERS), eq(workOrderId));
    }

    @Test
    void receiptListByPlant_allowedWithTheNewReadPermissionAlone() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_READ"), eq("PLANT"), eq(plantId))).thenReturn(true);
        when(productionReceiptRepository.findByPlant(eq(plantId), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class))).thenReturn(Page.empty());

        assertThatCode(() -> receiptService.listByPlant(plantId, null, null, null, null, null, null, null, null, null,
                PageRequest.of(0, 20))).doesNotThrowAnyException();
        verify(permissionGuard, atLeastOnce()).hasResourceAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_READ"), eq("PLANT"), eq(plantId));
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

        assertThatThrownBy(() -> receiptService.listCandidates(plantId, null, null, PageRequest.of(0, 20)))
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
        when(workOrderRepository.findReceiptCandidates(eq(plantId), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThatCode(() -> receiptService.listCandidates(plantId, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void receiptListByPlant_deniedWhenPlantScopedPermissionMissing() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_PRODUCTION_RECEIPT_MANAGE"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> receiptService.listByPlant(plantId, null, null, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
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

        assertThatThrownBy(() -> materialIssueService.listByPlant(plantId, null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq("PLANT"), eq(plantId));
    }

    /** G8b cancel: only the requester's permission to post issues, never approve alone. */
    @Test
    void cancelMaterialIssue_deniedWhenManagePermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> materialIssueService.cancelByRequester(workOrderId, UUID.randomUUID(),
                new MaterialIssueVersionedDecisionRequest(0L, 0L, "Picked the wrong lot"), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq(workOrderId));
    }

    /** G8b detail: readable by whoever may post, approve or override — the guard receives all three. */
    @Test
    void materialIssueDetail_deniedWhenNoneOfTheThreePermissionsIsHeld() {
        UUID issueId = UUID.randomUUID();
        List<String> expected = List.of("PERM_MATERIAL_ISSUE_MANAGE", "PERM_MATERIAL_ISSUE_APPROVE",
                "PERM_MATERIAL_ISSUE_OVERRIDE");
        when(workOrderPermissionGuard.hasMaterialIssueAccess(any(), eq(expected), eq(issueId))).thenReturn(false);

        assertThatThrownBy(() -> materialIssueService.getDetail(issueId)).isInstanceOf(AccessDeniedException.class);

        verify(workOrderPermissionGuard).hasMaterialIssueAccess(any(), eq(expected), eq(issueId));
    }

    @Test
    void materialIssueCandidates_deniedWhenManagePermissionMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> candidateService.candidates(workOrderId, null,
                MaterialIssueCandidateService.Mode.NORMAL, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_MATERIAL_ISSUE_MANAGE"), eq(workOrderId));
    }

    // ── G8c ─────────────────────────────────────────────────────────────────────────────────

    /** The report path had no method-security test before G8c; it is the command that now also records consumption. */
    @Test
    void reportProduction_deniedWhenExecutionManageMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_EXECUTION_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> productionExecutionService.report(workOrderId, new ProductionExecutionPostRequest(
                null, BigDecimal.ONE, null, null, null, null, null, 0L, null, null), "KEY-PE"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_EXECUTION_MANAGE"), eq(workOrderId));
    }

    @Test
    void listOperations_deniedWhenNeitherExecutionPermissionIsHeld() {
        UUID workOrderId = UUID.randomUUID();
        List<String> expected = List.of("PERM_PRODUCTION_EXECUTION_READ", "PERM_PRODUCTION_EXECUTION_MANAGE");
        when(workOrderPermissionGuard.hasAnyWorkOrderAccess(any(), eq(expected), eq(workOrderId))).thenReturn(false);

        assertThatThrownBy(() -> operationService.list(workOrderId)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support, operationLedger);
        verify(workOrderPermissionGuard).hasAnyWorkOrderAccess(any(), eq(expected), eq(workOrderId));
    }

    @Test
    void listOperations_allowedWithEitherExecutionPermission() {
        UUID workOrderId = UUID.randomUUID();
        List<String> expected = List.of("PERM_PRODUCTION_EXECUTION_READ", "PERM_PRODUCTION_EXECUTION_MANAGE");
        when(workOrderPermissionGuard.hasAnyWorkOrderAccess(any(), eq(expected), eq(workOrderId))).thenReturn(true);
        when(operationLedger.load(workOrderId)).thenReturn(List.of());

        assertThatCode(() -> operationService.list(workOrderId)).doesNotThrowAnyException();
    }

    @Test
    void operationInput_deniedWhenExecutionManageMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_EXECUTION_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> operationService.recordInput(workOrderId, UUID.randomUUID(),
                new com.erp.manufacturing.module.workorder.dto.operation.OperationInputRequest(0L, BigDecimal.ONE), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support, operationLedger);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_EXECUTION_MANAGE"), eq(workOrderId));
    }

    @Test
    void operationFinish_deniedWhenExecutionManageMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_EXECUTION_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> operationService.finish(workOrderId, UUID.randomUUID(),
                new com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest(0L), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support, operationLedger);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_EXECUTION_MANAGE"), eq(workOrderId));
    }

    /** Completion is a work order command; the shortfall approval is checked inside, on top of it (FE Q4). */
    @Test
    void completeProduction_deniedWhenWorkOrderManageMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> operationService.completeProduction(workOrderId,
                new com.erp.manufacturing.module.workorder.dto.operation.ProductionCompletionRequest(0L, "Ran out of steel"), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(support, operationLedger);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_MANAGE"), eq(workOrderId));
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
                    reservationRepository, new StockBalanceReservationService(stockBalanceRepository), organizationLookupService, support, mapper,
                    new WorkOrderIdempotency(mock(WorkOrderIdempotencyRecordRepository.class),
                            new IdempotencySupport(new ObjectMapper().findAndRegisterModules())));
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
                                                  UserLookupService userLookupService,
                                                  WorkOrderCostAccumulatorService costAccumulatorService,
                                                  InventoryLotLookupService inventoryLotLookupService,
                                                  ProductionExecutionRepository productionExecutionRepository,
                                                  SecurityAuditorAware auditorAware) {
            return new MaterialIssueService(
                    issueRepository, issueLineRepository, reservationService, movementService,
                    wipTransactionService, workOrderPermissionGuard, support,
                    new IdempotencySupport(new ObjectMapper()), mapper, new TraceIdProvider(),
                    userLookupService, costAccumulatorService, inventoryLotLookupService,
                    new WorkOrderIdempotency(mock(WorkOrderIdempotencyRecordRepository.class),
                            new IdempotencySupport(new ObjectMapper().findAndRegisterModules())),
                    productionExecutionRepository, auditorAware);
        }

        @Bean
        MaterialIssueCandidateService materialIssueCandidateService(MaterialReservationRepository reservationRepository,
                                                                    StockBalanceRepository stockBalanceRepository,
                                                                    WorkOrderExecutionSupport support) {
            return new MaterialIssueCandidateService(reservationRepository, new StockBalanceReservationService(stockBalanceRepository), support);
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
                                                          ManufacturingExecutionMapper mapper,
                                                          PermissionGuard permissionGuard) {
            return new ProductionReceiptService(
                    receiptRepository, workOrderRepository, receiptLineRepository, executionRepository,
                    dispositionRepository, movementService,
                    wipTransactionService, itemLookupService, userLookupService, auditorAware, allocationService, support,
                    mapper, new IdempotencySupport(new ObjectMapper()), new TraceIdProvider(),
                    new WorkOrderIdempotency(mock(WorkOrderIdempotencyRecordRepository.class),
                            new IdempotencySupport(new ObjectMapper())),
                    permissionGuard,
                    mock(ProductionReceiptContributionRepository.class),
                    mock(ReceiptContributionLedger.class),
                    mock(MaterialSourceLedger.class),
                    mock(ProductionQualityDecisionRepository.class));
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
                                                          WorkOrderRepository workOrderRepository,
                                                          ProductionExecutionRepository executionRepository,
                                                          WorkOrderOperationRepository operationRepository,
                                                          WorkOrderCostAccumulatorRepository workOrderCostAccumulatorRepository,
                                                          ItemStandardCostLookupService itemStandardCostLookupService) {
            return new WorkOrderVarianceService(
                    issueLineRepository, receiptLineRepository, wipTransactionRepository, workOrderRepository,
                    executionRepository, operationRepository, workOrderCostAccumulatorRepository,
                    itemStandardCostLookupService);
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

        @Bean WorkOrderCostAccumulatorService workOrderCostAccumulatorService() { return mock(WorkOrderCostAccumulatorService.class); }
        @Bean InventoryLotLookupService inventoryLotLookupService() { return mock(InventoryLotLookupService.class); }
        @Bean WorkOrderCostAccumulatorRepository workOrderCostAccumulatorRepository() { return mock(WorkOrderCostAccumulatorRepository.class); }
        @Bean ItemStandardCostLookupService itemStandardCostLookupService() { return mock(ItemStandardCostLookupService.class); }
        @Bean MaterialReservationRepository materialReservationRepository() { return mock(MaterialReservationRepository.class); }
        @Bean StockBalanceRepository stockBalanceRepository() { return mock(StockBalanceRepository.class); }
        @Bean MaterialIssueRepository materialIssueRepository() { return mock(MaterialIssueRepository.class); }
        @Bean MaterialIssueLineRepository materialIssueLineRepository() { return mock(MaterialIssueLineRepository.class); }
        @Bean InventoryMovementService inventoryMovementService() { return mock(InventoryMovementService.class); }
        @Bean ProductionReceiptRepository productionReceiptRepository() { return mock(ProductionReceiptRepository.class); }
        @Bean ProductionReceiptLineRepository productionReceiptLineRepository() { return mock(ProductionReceiptLineRepository.class); }
        @Bean WipTransactionRepository wipTransactionRepository() { return mock(WipTransactionRepository.class); }
        @Bean WorkOrderRepository workOrderRepository() { return mock(WorkOrderRepository.class); }

        @Bean OperationLedger operationLedger() { return mock(OperationLedger.class); }
        @Bean WorkOrderOperationInputRepository workOrderOperationInputRepository() { return mock(WorkOrderOperationInputRepository.class); }
        @Bean ProductionExecutionConsumptionRepository productionExecutionConsumptionRepository() {
            return mock(ProductionExecutionConsumptionRepository.class);
        }

        @Bean
        ProductionExecutionService productionExecutionService(ProductionExecutionRepository executionRepository,
                                                              WorkOrderOperationRepository operationRepository,
                                                              WorkOrderRepository workOrderRepository,
                                                              WipTransactionService wipTransactionService,
                                                              WorkOrderExecutionSupport support,
                                                              ManufacturingExecutionMapper mapper,
                                                              SecurityAuditorAware auditorAware,
                                                              UserLookupService userLookupService,
                                                              WorkOrderCostAccumulatorService costAccumulatorService,
                                                              OperationLedger ledger,
                                                              ProductionExecutionConsumptionRepository consumptionRepository,
                                                              MaterialIssueLineRepository issueLineRepository) {
            return new ProductionExecutionService(executionRepository, operationRepository, workOrderRepository,
                    wipTransactionService, support, mapper, new IdempotencySupport(new ObjectMapper()),
                    new TraceIdProvider(), auditorAware, userLookupService, costAccumulatorService, ledger,
                    consumptionRepository, issueLineRepository,
                    mock(com.erp.manufacturing.module.workorder.repository.MaterialReturnLineRepository.class));
        }

        @Bean
        OperationService operationService(OperationLedger ledger,
                                          WorkOrderExecutionSupport support,
                                          WorkOrderOperationInputRepository inputRepository,
                                          MaterialIssueLineRepository issueLineRepository,
                                          MaterialIssueRepository issueRepository,
                                          WorkOrderPermissionGuard workOrderPermissionGuard,
                                          SecurityAuditorAware auditorAware) {
            return new OperationService(ledger, support, inputRepository, issueLineRepository, issueRepository,
                    new WorkOrderIdempotency(mock(WorkOrderIdempotencyRecordRepository.class),
                            new IdempotencySupport(new ObjectMapper().findAndRegisterModules())),
                    workOrderPermissionGuard, auditorAware);
        }
    }
}
