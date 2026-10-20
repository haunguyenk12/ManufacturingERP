package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** G8b: an endpoint identified by the material issue alone is authorised on its work order's plant. */
@ExtendWith(MockitoExtension.class)
class WorkOrderPermissionGuardTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock PermissionGuard permissionGuard;
    @Mock MaterialIssueRepository materialIssueRepository;
    @Mock MaterialReturnRepository materialReturnRepository;
    @InjectMocks WorkOrderPermissionGuard guard;

    private final Authentication auth = new UsernamePasswordAuthenticationToken("user", null, List.of());
    private final List<String> codes = List.of("PERM_MATERIAL_ISSUE_MANAGE", "PERM_MATERIAL_ISSUE_APPROVE");

    @Test
    void materialIssueAccess_isGrantedWhenAnyCodeHoldsOnTheWorkOrdersPlant() {
        UUID issueId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(materialIssueRepository.findWorkOrderIdByIssueId(issueId)).thenReturn(Optional.of(workOrderId));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(
                WorkOrder.builder().workOrderId(workOrderId).plant(Plant.builder().plantId(plantId).build()).build()));
        when(permissionGuard.hasResourceAccess(auth, "PERM_MATERIAL_ISSUE_MANAGE", "PLANT", plantId)).thenReturn(false);
        when(permissionGuard.hasResourceAccess(auth, "PERM_MATERIAL_ISSUE_APPROVE", "PLANT", plantId)).thenReturn(true);

        assertThat(guard.hasMaterialIssueAccess(auth, codes, issueId)).isTrue();
    }

    @Test
    void materialIssueAccess_isDeniedWhenNoCodeHolds() {
        UUID issueId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(materialIssueRepository.findWorkOrderIdByIssueId(issueId)).thenReturn(Optional.of(workOrderId));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(
                WorkOrder.builder().workOrderId(workOrderId).plant(Plant.builder().plantId(plantId).build()).build()));

        assertThat(guard.hasMaterialIssueAccess(auth, codes, issueId)).isFalse();
        verify(permissionGuard).hasResourceAccess(eq(auth), eq("PERM_MATERIAL_ISSUE_APPROVE"), eq("PLANT"), eq(plantId));
    }

    @Test
    void materialIssueAccess_unknownIssue_isDeniedWithoutAPermissionLookup() {
        UUID issueId = UUID.randomUUID();
        when(materialIssueRepository.findWorkOrderIdByIssueId(issueId)).thenReturn(Optional.empty());

        assertThat(guard.hasMaterialIssueAccess(auth, codes, issueId)).isFalse();
        verifyNoInteractions(permissionGuard, workOrderRepository);
    }

    @Test
    void materialIssueAccess_nullIssue_isDenied() {
        assertThat(guard.hasMaterialIssueAccess(auth, codes, null)).isFalse();
        verifyNoInteractions(materialIssueRepository);
        verify(permissionGuard, org.mockito.Mockito.never()).hasResourceAccess(any(), any(), any(), any());
    }

    /** G8d: a return is authorised on its work order's plant, like an issue. */
    @Test
    void materialReturnAccess_isGrantedWhenAnyCodeHoldsOnTheWorkOrdersPlant() {
        UUID returnId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        when(materialReturnRepository.findWorkOrderIdByMaterialReturnId(returnId)).thenReturn(Optional.of(workOrderId));
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(
                WorkOrder.builder().workOrderId(workOrderId).plant(Plant.builder().plantId(plantId).build()).build()));
        when(permissionGuard.hasResourceAccess(auth, "PERM_MATERIAL_RETURN_READ", "PLANT", plantId)).thenReturn(true);

        assertThat(guard.hasMaterialReturnAccess(auth, List.of("PERM_MATERIAL_RETURN_READ"), returnId)).isTrue();
    }

    @Test
    void materialReturnAccess_unknownReturn_isDeniedWithoutAPermissionLookup() {
        UUID returnId = UUID.randomUUID();
        when(materialReturnRepository.findWorkOrderIdByMaterialReturnId(returnId)).thenReturn(Optional.empty());

        assertThat(guard.hasMaterialReturnAccess(auth, List.of("PERM_MATERIAL_RETURN_READ"), returnId)).isFalse();
        verifyNoInteractions(permissionGuard, workOrderRepository);
    }
}
