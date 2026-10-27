package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.costing.service.ItemStandardCostLookupService;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.delivery.service.DeliveryPermissionGuard;
import com.erp.manufacturing.module.inventory.service.StockPoolLookupService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.FinalizeRequest;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.RunRequest;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateRequest;
import com.erp.manufacturing.module.workorder.repository.ProductionCostAdjustmentRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionCostBaselineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionCostRateRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionCostRunRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderIdempotencyRecordRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * G11: who may see production cost and who may change it (D6, FE §1.3). Each deny case pins the exact permission code
 * through the guard call (R2); each allow case proves the call got past the guard by reaching the first thing the
 * service does — the required {@code Idempotency-Key} for commands, the work order lookup for reads.
 */
@SpringJUnitConfig(ProductionCostMethodSecurityTest.Config.class)
@DisplayName("Production cost method security (G11)")
class ProductionCostMethodSecurityTest {

    private static final List<String> COST_VIEW = List.of("PERM_WORK_ORDER_VARIANCE_READ", "PERM_PRODUCTION_COST_CALCULATE");
    private static final List<String> CHECKLIST = List.of("PERM_WORK_ORDER_VARIANCE_READ", "PERM_PRODUCTION_COST_CALCULATE",
            "PERM_WORK_ORDER_CLOSE", "PERM_WORK_ORDER_READ", "PERM_WIP_READ");

    @Autowired ProductionCostRateService rateService;
    @Autowired ProductionCostService costService;
    @Autowired ProductionCostCloseService closeService;
    @Autowired ProductionCostBaselineService baselineService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired WorkOrderPermissionGuard workOrderPermissionGuard;
    @Autowired DeliveryPermissionGuard deliveryPermissionGuard;
    @Autowired ProductionCostRateRepository rateRepository;
    @Autowired WorkOrderRepository workOrderRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, workOrderPermissionGuard, deliveryPermissionGuard, rateRepository, workOrderRepository);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void saveRate_deniedWithoutCostRateManage() {
        UUID plantId = UUID.randomUUID();

        assertThatThrownBy(() -> rateService.save(plantId, UUID.randomUUID(), rateRequest(), "KEY"))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_COST_RATE_MANAGE"), eq("PLANT"), eq(plantId));
        verifyNoInteractions(rateRepository);
    }

    @Test
    void saveRate_allowedWithCostRateManage_reachesTheRequiredKey() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_COST_RATE_MANAGE"), eq("PLANT"), eq(plantId))).thenReturn(true);

        assertThatThrownBy(() -> rateService.save(plantId, UUID.randomUUID(), rateRequest(), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.MISSING_REQUIRED_FIELD));
    }

    /** FE R28: the rate table opens with the rate permission alone — no cost view, no work center master. */
    @Test
    void listRates_deniedWithoutAny_checksRateAndBothCostViewCodes() {
        UUID plantId = UUID.randomUUID();

        assertThatThrownBy(() -> rateService.list(plantId, null, null, org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_COST_RATE_MANAGE"), eq("PLANT"), eq(plantId));
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_WORK_ORDER_VARIANCE_READ"), eq("PLANT"), eq(plantId));
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_PRODUCTION_COST_CALCULATE"), eq("PLANT"), eq(plantId));
    }

    @Test
    void costDetail_deniedWithoutCostView() {
        UUID workOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> costService.get(workOrderId, null, 0, 20)).isInstanceOf(AccessDeniedException.class);

        verify(workOrderPermissionGuard).hasAnyWorkOrderAccess(any(), eq(COST_VIEW), eq(workOrderId));
        verifyNoInteractions(workOrderRepository);
    }

    /** FE §1.3: the shop floor reads the checklist with PERM_WIP_READ (money is then absent, see the service). */
    @Test
    void checklist_allowedWithTheChecklistCodes_reachesTheWorkOrderLookup() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasAnyWorkOrderAccess(any(), eq(CHECKLIST), eq(workOrderId))).thenReturn(true);

        assertThatThrownBy(() -> costService.checklist(workOrderId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
        verify(workOrderRepository).findWithDetailsByWorkOrderId(workOrderId);
    }

    @Test
    void createRun_deniedWithoutCalculate() {
        UUID workOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> costService.createRun(workOrderId, new RunRequest(0L, "cs1-x"), "KEY"))
                .isInstanceOf(AccessDeniedException.class);

        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_COST_CALCULATE"), eq(workOrderId));
        verifyNoInteractions(workOrderRepository);
    }

    /** FE Q28 / A42: the close permission alone is a blind close — refused. */
    @Test
    void finalize_deniedWithClosePermissionButNoCostView() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_CLOSE"), eq(workOrderId))).thenReturn(true);

        assertThatThrownBy(() -> closeService.finalizeAndClose(workOrderId, finalizeRequest(), "KEY"))
                .isInstanceOf(AccessDeniedException.class);

        verify(workOrderPermissionGuard).hasAnyWorkOrderAccess(any(), eq(COST_VIEW), eq(workOrderId));
        verifyNoInteractions(workOrderRepository);
    }

    @Test
    void finalize_allowedWithCloseAndCostView_reachesTheRequiredKey() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_CLOSE"), eq(workOrderId))).thenReturn(true);
        when(workOrderPermissionGuard.hasAnyWorkOrderAccess(any(), eq(COST_VIEW), eq(workOrderId))).thenReturn(true);

        assertThatThrownBy(() -> closeService.finalizeAndClose(workOrderId, finalizeRequest(), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.MISSING_REQUIRED_FIELD));
    }

    @Test
    void baseline_deniedWithoutCostView() {
        UUID workOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> baselineService.get(workOrderId)).isInstanceOf(AccessDeniedException.class);

        verify(workOrderPermissionGuard).hasAnyWorkOrderAccess(any(), eq(COST_VIEW), eq(workOrderId));
    }

    @Test
    void deliveryCost_deniedWithoutDeliveryRead() {
        UUID deliveryId = UUID.randomUUID();

        assertThatThrownBy(() -> closeService.deliveryCost(deliveryId)).isInstanceOf(AccessDeniedException.class);

        verify(deliveryPermissionGuard).canReadDelivery(any(), eq(deliveryId));
    }

    private static RateRequest rateRequest() {
        return new RateRequest(0, "VND", new BigDecimal("6000"), new BigDecimal("3000"), "Operating rate");
    }

    private static FinalizeRequest finalizeRequest() {
        return new FinalizeRequest(0L, "cs1-x", UUID.randomUUID(), 1, "Cost reconciled");
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean ProductionCostRateRepository rateRepository() { return mock(ProductionCostRateRepository.class); }
        @Bean WorkOrderRepository workOrderRepository() { return mock(WorkOrderRepository.class); }

        @Bean
        WorkOrderIdempotency idempotency() {
            return new WorkOrderIdempotency(mock(WorkOrderIdempotencyRecordRepository.class),
                    new IdempotencySupport(new ObjectMapper()));
        }

        @Bean
        ProductionCostRateService rateService(ProductionCostRateRepository rateRepository, WorkOrderIdempotency idempotency) {
            return new ProductionCostRateService(rateRepository, mock(WorkCenterLookupService.class), idempotency,
                    mock(UserLookupService.class));
        }

        @Bean
        ProductionCostBaselineService baselineService(ProductionCostRateService rateService) {
            return new ProductionCostBaselineService(mock(ProductionCostBaselineRepository.class), rateService,
                    mock(ItemStandardCostLookupService.class), mock(StockPoolLookupService.class),
                    mock(SecurityAuditorAware.class), mock(UserLookupService.class));
        }

        @Bean
        ProductionCostService costService(WorkOrderRepository workOrderRepository, WorkOrderIdempotency idempotency,
                                          PermissionGuard permissionGuard) {
            return new ProductionCostService(workOrderRepository, mock(ProductionCostRunRepository.class),
                    mock(ProductionCostSourceReader.class), idempotency, permissionGuard,
                    mock(OrganizationLookupService.class), mock(UserLookupService.class), new ObjectMapper());
        }

        @Bean
        ProductionCostCloseService closeService(ProductionCostService costService, WorkOrderRepository workOrderRepository,
                                                WorkOrderIdempotency idempotency, PermissionGuard permissionGuard) {
            return new ProductionCostCloseService(costService, mock(ProductionCostRunRepository.class),
                    mock(ProductionCostAdjustmentRepository.class), workOrderRepository, idempotency, permissionGuard,
                    mock(SecurityAuditorAware.class), mock(DeliveryLookupService.class), mock(EntityManager.class));
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "workOrderPermissionGuard")
        WorkOrderPermissionGuard workOrderPermissionGuard() { return mock(WorkOrderPermissionGuard.class); }

        @Bean(name = "deliveryPermissionGuard")
        DeliveryPermissionGuard deliveryPermissionGuard() { return mock(DeliveryPermissionGuard.class); }
    }
}
