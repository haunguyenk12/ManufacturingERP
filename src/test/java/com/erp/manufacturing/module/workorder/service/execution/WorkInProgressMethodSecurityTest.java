package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnLineRequest;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnPostRequest;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(WorkInProgressMethodSecurityTest.Config.class)
@DisplayName("WIP and material return method security (G8d)")
class WorkInProgressMethodSecurityTest {

    @Autowired MaterialReturnService returnService;
    @Autowired WorkOrderWipService wipService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired WorkOrderPermissionGuard workOrderPermissionGuard;
    @Autowired MaterialReturnRepository returnRepository;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired WorkOrderExecutionSupport support;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, workOrderPermissionGuard, returnRepository, workOrderRepository, support);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static MaterialReturnPostRequest request() {
        return new MaterialReturnPostRequest(0L, "Unused after the run",
                List.of(new MaterialReturnLineRequest(UUID.randomUUID(), BigDecimal.ONE)));
    }

    @Test
    void post_deniedWithoutMaterialReturnManage() {
        UUID workOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> returnService.post(workOrderId, request(), "KEY"))
                .isInstanceOf(AccessDeniedException.class);

        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_MATERIAL_RETURN_MANAGE"), eq(workOrderId));
        verifyNoInteractions(returnRepository);
    }

    @Test
    void post_allowedWithMaterialReturnManage_reachesTheReplayLookup() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderPermissionGuard.hasWorkOrderAccess(any(), eq("PERM_MATERIAL_RETURN_MANAGE"), eq(workOrderId)))
                .thenReturn(true);
        when(returnRepository.findWithWorkOrderByIdempotencyKey("KEY")).thenReturn(Optional.empty());
        when(support.findWorkOrder(workOrderId)).thenThrow(new IllegalStateException("reached the service"));

        assertThatThrownBy(() -> returnService.post(workOrderId, request(), "KEY"))
                .isInstanceOf(IllegalStateException.class);
        verify(returnRepository).findWithWorkOrderByIdempotencyKey("KEY");
    }

    @Test
    void get_deniedWithoutAnyReturnPermission_checksReadAndManage() {
        UUID returnId = UUID.randomUUID();

        assertThatThrownBy(() -> returnService.get(returnId)).isInstanceOf(AccessDeniedException.class);

        verify(workOrderPermissionGuard).hasMaterialReturnAccess(any(),
                eq(List.of("PERM_MATERIAL_RETURN_READ", "PERM_MATERIAL_RETURN_MANAGE")), eq(returnId));
        verifyNoInteractions(returnRepository);
    }

    @Test
    void listByPlant_allowedWithReturnReadAlone() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_MATERIAL_RETURN_READ"), eq("PLANT"), eq(plantId)))
                .thenReturn(true);
        when(returnRepository.findByPlant(eq(plantId), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        returnService.listByPlant(plantId, null, null, null, null, PageRequest.of(0, 20));

        verify(returnRepository).findByPlant(eq(plantId), any(), any(), any(), any(), any());
    }

    @Test
    void listByPlant_deniedWithoutReturnReadOrManage() {
        UUID plantId = UUID.randomUUID();

        assertThatThrownBy(() -> returnService.listByPlant(plantId, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_MATERIAL_RETURN_READ"), eq("PLANT"), eq(plantId));
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_MATERIAL_RETURN_MANAGE"), eq("PLANT"), eq(plantId));
        verifyNoInteractions(returnRepository);
    }

    @Test
    void candidates_deniedWithoutMaterialReturnManage() {
        UUID workOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> returnService.candidates(workOrderId, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_MATERIAL_RETURN_MANAGE"), eq(workOrderId));
        verifyNoInteractions(support);
    }

    @Test
    void wipList_deniedWithoutWipReadOnThePlant() {
        UUID plantId = UUID.randomUUID();

        assertThatThrownBy(() -> wipService.listByPlant(plantId, null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_WIP_READ"), eq("PLANT"), eq(plantId));
        verifyNoInteractions(workOrderRepository);
    }

    @Test
    void wipList_allowedWithWipRead() {
        UUID plantId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_WIP_READ"), eq("PLANT"), eq(plantId))).thenReturn(true);
        when(workOrderRepository.searchWip(eq(plantId), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        wipService.listByPlant(plantId, null, null, null, null, null, PageRequest.of(0, 20));

        verify(workOrderRepository).searchWip(eq(plantId), any(), any(), any(), any(), any(), any());
    }

    @Test
    void wipDetail_deniedWithoutWipRead() {
        UUID workOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> wipService.get(workOrderId, null)).isInstanceOf(AccessDeniedException.class);

        verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_WIP_READ"), eq(workOrderId));
        verifyNoInteractions(support);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean MaterialReturnRepository materialReturnRepository() { return mock(MaterialReturnRepository.class); }
        @Bean WorkOrderRepository workOrderRepository() { return mock(WorkOrderRepository.class); }
        @Bean WorkOrderExecutionSupport workOrderExecutionSupport() { return mock(WorkOrderExecutionSupport.class); }

        @Bean
        MaterialReturnService materialReturnService(MaterialReturnRepository returnRepository, WorkOrderExecutionSupport support) {
            return new MaterialReturnService(returnRepository, mock(MaterialReturnLineRepository.class),
                    mock(MaterialIssueLineRepository.class), mock(MaterialSourceLedger.class),
                    mock(InventoryMovementService.class), mock(WipTransactionService.class), support,
                    new IdempotencySupport(new ObjectMapper()), new TraceIdProvider(), mock(UserLookupService.class),
                    mock(SecurityAuditorAware.class));
        }

        @Bean
        WorkOrderWipService workOrderWipService(WorkOrderRepository workOrderRepository, WorkOrderExecutionSupport support) {
            return new WorkOrderWipService(workOrderRepository, mock(ProductionExecutionRepository.class),
                    mock(ProductionReceiptRepository.class), mock(OperationLedger.class), mock(MaterialSourceLedger.class),
                    support);
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean(name = "workOrderPermissionGuard")
        WorkOrderPermissionGuard workOrderPermissionGuard() { return mock(WorkOrderPermissionGuard.class); }
    }
}
