package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanningPermissionGuard tests")
class PlanningPermissionGuardTest {

    @Mock PermissionGuard permissionGuard;

    PlanningPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PlanningPermissionGuard(permissionGuard);
    }

    @Test
    void canEstimate_delegatesToResourceScopePermission() {
        UUID warehouseId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());
        ProductionEstimateRequest request = new ProductionEstimateRequest(
                UUID.randomUUID(), ScopeResourceType.WAREHOUSE, warehouseId, BigDecimal.ONE);
        when(permissionGuard.hasResourceAccess(auth, "PERM_PLANNING_READ", "WAREHOUSE", warehouseId))
                .thenReturn(true);

        assertThat(guard.canEstimate(auth, request)).isTrue();
    }

    @Test
    void canEstimate_missingRequestOrScopeDenied() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", null, java.util.List.of());

        assertThat(guard.canEstimate(auth, null)).isFalse();
        assertThat(guard.canEstimate(auth, new ProductionEstimateRequest(
                UUID.randomUUID(), null, UUID.randomUUID(), BigDecimal.ONE))).isFalse();

        verifyNoInteractions(permissionGuard);
    }
}
