package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentRequest;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.service.query.CapacityBoardService;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Rule R2. {@code adjust} authorizes via {@code workOrderPermissionGuard} — the same generic,
 * permission-code-keyed guard {@code WorkOrderService} uses, reused here rather than a new
 * permission-guard component (see plan's design decisions).
 */
@SpringJUnitConfig(ScheduleAdjustmentServiceMethodSecurityTest.Config.class)
@DisplayName("ScheduleAdjustmentService method security")
class ScheduleAdjustmentServiceMethodSecurityTest {

    @Autowired ScheduleAdjustmentService scheduleAdjustmentService;
    @Autowired WorkOrderPermissionGuard workOrderPermissionGuard;
    @Autowired WorkOrderOperationRepository workOrderOperationRepository;
    @Autowired CapacityBoardService capacityBoardService;

    private static final UUID WORK_ORDER_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-01-05T08:00:00Z");
    private static final Instant END = Instant.parse("2026-01-05T09:00:00Z");

    @BeforeEach
    void setUp() {
        reset(workOrderPermissionGuard, workOrderOperationRepository, capacityBoardService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adjust_deniedWhenCapacityManageScopeMissing() {
        when(workOrderPermissionGuard.hasWorkOrderAccess(
                any(), eq("PERM_CAPACITY_MANAGE"), eq(WORK_ORDER_ID))).thenReturn(false);

        assertThatThrownBy(() -> scheduleAdjustmentService.adjust(WORK_ORDER_ID, OPERATION_ID,
                new ScheduleAdjustmentRequest(START, END, "Reason", 0L)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderOperationRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_CAPACITY_MANAGE"), eq(WORK_ORDER_ID));
    }

    @Test
    void adjust_allowedWhenCapacityManageScopePresent() {
        when(workOrderPermissionGuard.hasWorkOrderAccess(
                any(), eq("PERM_CAPACITY_MANAGE"), eq(WORK_ORDER_ID))).thenReturn(true);

        Plant plant = Plant.builder().plantId(UUID.randomUUID()).code("P1").name("Plant 1")
                .status(OrganizationStatus.ACTIVE).build();
        WorkOrder workOrder = WorkOrder.builder().workOrderId(WORK_ORDER_ID).plant(plant).workOrderNo("WO-1")
                .plannedQuantity(BigDecimal.TEN).status(WorkOrderStatus.RELEASED).build();
        WorkCenter workCenter = WorkCenter.builder().workCenterId(UUID.randomUUID()).code("WC-1").name("WC 1")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build();
        WorkOrderOperation operation = WorkOrderOperation.builder()
                .workOrderOperationId(OPERATION_ID).workOrder(workOrder).sequence(1).name("Op")
                .workCenterCode("WC-1").workCenter(workCenter)
                .setupMinutes(BigDecimal.ZERO).runMinutesPerUnit(BigDecimal.ZERO)
                .plannedStartAt(START).plannedEndAt(END).build();
        operation.setVersion(0L);
        when(workOrderOperationRepository.findByWorkOrderOperationIdAndWorkOrderWorkOrderId(OPERATION_ID, WORK_ORDER_ID))
                .thenReturn(Optional.of(operation));
        when(workOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc(WORK_ORDER_ID))
                .thenReturn(List.of(operation));
        when(workOrderOperationRepository.save(operation)).thenReturn(operation);
        when(capacityBoardService.buildDayContext(any(), any(), any(), any()))
                .thenReturn(new CapacityBoardService.DayContext(Map.of(), Map.of(), Map.of()));

        assertThatCode(() -> scheduleAdjustmentService.adjust(WORK_ORDER_ID, OPERATION_ID,
                new ScheduleAdjustmentRequest(START, END.plusSeconds(1800), "Reason", 0L)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        ScheduleAdjustmentService scheduleAdjustmentService(WorkOrderOperationRepository workOrderOperationRepository,
                                                              CapacityBoardService capacityBoardService) {
            return new ScheduleAdjustmentService(workOrderOperationRepository, capacityBoardService);
        }

        @Bean(name = "workOrderPermissionGuard")
        WorkOrderPermissionGuard workOrderPermissionGuard() { return mock(WorkOrderPermissionGuard.class); }

        @Bean WorkOrderOperationRepository workOrderOperationRepository() { return mock(WorkOrderOperationRepository.class); }
        @Bean CapacityBoardService capacityBoardService() { return mock(CapacityBoardService.class); }
    }
}
