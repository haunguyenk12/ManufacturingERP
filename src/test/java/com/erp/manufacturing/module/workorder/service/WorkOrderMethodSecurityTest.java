package com.erp.manufacturing.module.workorder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.module.workorder.repository.WorkOrderIdempotencyRecordRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.planning.service.WorkOrderPlanningLinkService;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.routing.service.RoutingLookupService;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.execution.WorkOrderComponentIssueRequest;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderCreateRequest;
import com.erp.manufacturing.module.workorder.mapper.WorkOrderMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.execution.MaterialIssueService;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.execution.ProductionReceiptService;
import com.erp.manufacturing.module.workorder.service.execution.WipTransactionService;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(WorkOrderMethodSecurityTest.Config.class)
@DisplayName("WorkOrderService method security")
class WorkOrderMethodSecurityTest {

    @Autowired WorkOrderService workOrderService;
    @Autowired com.erp.manufacturing.module.workorder.service.query.WorkOrderSchedulePreviewService schedulePreviewService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired WorkOrderPermissionGuard workOrderPermissionGuard;
    @Autowired PlantRepository plantRepository;
    @Autowired WorkOrderRepository workOrderRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, workOrderPermissionGuard, plantRepository, workOrderRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_deniedWhenPlantManageScopeMissing() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_WORK_ORDER_MANAGE"), eq("PLANT"), eq(plantId)))
                .thenReturn(false);

        assertThatThrownBy(() -> workOrderService.create(plantId, new WorkOrderCreateRequest(
                "WO-001", UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(plantRepository);
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_WORK_ORDER_MANAGE"), eq("PLANT"), eq(plantId));
    }

    /**
     * Since F4 {@code createFromMrp} no longer delegates to {@code create}, so this is the only
     * check standing between a planner and a work order — it must be the supply-suggestion
     * permission, not the work-order one.
     */
    @Test
    void createFromMrp_deniedWhenSupplySuggestionManageScopeMissing() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(
                any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq("PLANT"), eq(plantId))).thenReturn(false);

        assertThatThrownBy(() -> workOrderService.createFromMrp(plantId, new WorkOrderCreateRequest(
                "WO-001", UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, null, null, null), null, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(plantRepository, workOrderRepository);
        verify(permissionGuard).hasResourceAccess(
                any(), eq("PERM_SUPPLY_SUGGESTION_MANAGE"), eq("PLANT"), eq(plantId));
    }

    /** G8a: the schedule preview is a read — PERM_WORK_ORDER_READ, never MANAGE. */
    @Test
    void schedulePreview_deniedWhenWorkOrderReadScopeMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_READ"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> schedulePreviewService.preview(workOrderId, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_READ"), eq(workOrderId));
    }

    @Test
    void schedulePreview_allowedWhenWorkOrderReadScopePresent() {
        WorkOrder workOrder = workOrder();
        workOrder.setVersion(0L);
        when(workOrderPermissionGuard.hasWorkOrderAccess(
                any(), eq("PERM_WORK_ORDER_READ"), eq(workOrder.getWorkOrderId()))).thenReturn(true);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatCode(() -> schedulePreviewService.preview(workOrder.getWorkOrderId(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void plan_deniedWhenWorkOrderManageScopeMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> workOrderService.plan(workOrderId,
                new com.erp.manufacturing.module.workorder.dto.core.WorkOrderPlanRequest(0L, null), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_MANAGE"), eq(workOrderId));
    }

    @Test
    void get_deniedWhenWorkOrderReadScopeMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_READ"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> workOrderService.get(workOrderId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_READ"), eq(workOrderId));
    }

    @Test
    void get_allowedWhenWorkOrderReadScopePresent() {
        WorkOrder workOrder = workOrder();
        when(workOrderPermissionGuard.hasWorkOrderAccess(
                any(), eq("PERM_WORK_ORDER_READ"), eq(workOrder.getWorkOrderId()))).thenReturn(true);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));

        assertThatCode(() -> workOrderService.get(workOrder.getWorkOrderId())).doesNotThrowAnyException();
    }

    @Test
    void issueComponent_deniedWhenExecuteScopeMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_EXECUTE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> workOrderService.issueComponent(workOrderId, new WorkOrderComponentIssueRequest(
                UUID.randomUUID(), UUID.randomUUID(), null, null, BigDecimal.ONE, null), "KEY-1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_EXECUTE"), eq(workOrderId));
    }

    @Test
    void close_deniedWhenManageScopeMissing() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_MANAGE"), eq(workOrderId)))
                .thenReturn(false);

        assertThatThrownBy(() -> workOrderService.close(workOrderId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderRepository);
        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_WORK_ORDER_MANAGE"), eq(workOrderId));
    }

    @Test
    void close_allowedWhenManageScopePresent() {
        WorkOrder workOrder = workOrder();
        workOrder.setStatus(WorkOrderStatus.COMPLETED);
        when(workOrderPermissionGuard.hasWorkOrderAccess(
                any(), eq("PERM_WORK_ORDER_MANAGE"), eq(workOrder.getWorkOrderId()))).thenReturn(true);
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrder.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder));
        when(workOrderRepository.saveAndFlush(workOrder)).thenReturn(workOrder);

        assertThatCode(() -> workOrderService.close(workOrder.getWorkOrderId())).doesNotThrowAnyException();
    }

    private WorkOrder workOrder() {
        Company company = Company.builder()
                .companyId(UUID.randomUUID())
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Plant plant = Plant.builder()
                .plantId(UUID.randomUUID())
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
        Item product = Item.builder()
                .itemId(UUID.randomUUID())
                .company(company)
                .code("FG-100")
                .name("Finished Good")
                .type(ItemType.FINISHED_GOOD)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
        return WorkOrder.builder()
                .workOrderId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .workOrderNo("WO-001")
                .productItem(product)
                .bom(BomHeader.builder()
                        .bomId(UUID.randomUUID())
                        .company(company)
                        .parentItem(product)
                        .revision("R1")
                        .status(BomStatus.ACTIVE)
                        .lines(new ArrayList<>())
                        .build())
                .bomRevision("R1")
                .outputWarehouse(Warehouse.builder()
                        .warehouseId(UUID.randomUUID())
                        .plant(plant)
                        .code("WH1")
                        .name("Warehouse 1")
                        .type(WarehouseType.FINISHED_GOODS)
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .plannedQuantity(BigDecimal.TEN)
                .completedQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.DRAFT)
                .componentLines(new ArrayList<>())
                .build();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean WorkOrderDemandAllocationService workOrderDemandAllocationService() {
            return mock(WorkOrderDemandAllocationService.class);
        }

        @Bean
        WorkOrderService workOrderService(WorkOrderRepository workOrderRepository,
                                          OrganizationLookupService organizationLookupService,
                                          ItemLookupService itemLookupService,
                                          BomLookupService bomLookupService,
                                          RoutingLookupService routingLookupService,
                                          MaterialReservationService materialReservationService,
                                          MaterialIssueService materialIssueService,
                                          WipTransactionService wipTransactionService,
                                          ProductionReceiptService productionReceiptService,
                                          WorkOrderDemandAllocationService allocationService,
                                          MaterialReservationRepository reservationRepository,
                                          WorkOrderReleaseGate releaseGate,
                                          WorkCalendarLookupService workCalendarLookupService,
                                          WorkOrderMapper mapper) {
            WorkOrderIdempotency idempotency = new WorkOrderIdempotency(
                    mock(WorkOrderIdempotencyRecordRepository.class),
                    new IdempotencySupport(new ObjectMapper().findAndRegisterModules()));
            return new WorkOrderService(
                    workOrderRepository,
                    organizationLookupService,
                    itemLookupService,
                    bomLookupService,
                    routingLookupService,
                    materialReservationService,
                    materialIssueService,
                    wipTransactionService,
                    productionReceiptService,
                    allocationService,
                    reservationRepository,
                    releaseGate,
                    new WorkOrderScheduler(workCalendarLookupService),
                    mock(MaterialIssueRepository.class),
                    mock(WorkOrderPlanningLinkService.class),
                    idempotency,
                    mock(SecurityAuditorAware.class),
                    mapper);
        }

        @Bean
        com.erp.manufacturing.module.workorder.service.query.WorkOrderSchedulePreviewService workOrderSchedulePreviewService(
                WorkOrderRepository workOrderRepository, WorkCalendarLookupService workCalendarLookupService) {
            return new com.erp.manufacturing.module.workorder.service.query.WorkOrderSchedulePreviewService(
                    workOrderRepository, new WorkOrderScheduler(workCalendarLookupService),
                    mock(com.erp.manufacturing.module.workorder.service.query.CapacityLoadCalculator.class));
        }

        @Bean MaterialReservationRepository materialReservationRepository() {
            return mock(MaterialReservationRepository.class);
        }

        @Bean
        WorkOrderReleaseGate workOrderReleaseGate() {
            return mock(WorkOrderReleaseGate.class);
        }

        @Bean
        WorkCalendarLookupService workCalendarLookupService() {
            return mock(WorkCalendarLookupService.class);
        }

        @Bean
        WorkOrderMapper workOrderMapper() {
            return new WorkOrderMapper();
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean(name = "workOrderPermissionGuard")
        WorkOrderPermissionGuard workOrderPermissionGuard() {
            return mock(WorkOrderPermissionGuard.class);
        }

        @Bean
        WorkOrderRepository workOrderRepository() {
            return mock(WorkOrderRepository.class);
        }

        @Bean
        PlantRepository plantRepository() {
            return mock(PlantRepository.class);
        }

        @Bean
        OrganizationLookupService organizationLookupService() {
            return mock(OrganizationLookupService.class);
        }

        @Bean
        ItemLookupService itemLookupService() {
            return mock(ItemLookupService.class);
        }

        @Bean
        BomLookupService bomLookupService() {
            return mock(BomLookupService.class);
        }

        @Bean
        RoutingLookupService routingLookupService() {
            return mock(RoutingLookupService.class);
        }

        @Bean
        MaterialReservationService materialReservationService() {
            return mock(MaterialReservationService.class);
        }

        @Bean
        MaterialIssueService materialIssueService() {
            return mock(MaterialIssueService.class);
        }

        @Bean
        WipTransactionService wipTransactionService() {
            return mock(WipTransactionService.class);
        }

        @Bean
        ProductionReceiptService productionReceiptService() {
            return mock(ProductionReceiptService.class);
        }
    }
}
